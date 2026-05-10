package com.lzt.summaryofslides.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryImageEntity
import com.lzt.summaryofslides.data.db.EntryPdfEntity
import com.lzt.summaryofslides.data.db.SlideAnalysisEntity
import com.lzt.summaryofslides.llm.OpenAiCompatClient
import com.lzt.summaryofslides.llm.ZhiPuDocParser
import com.lzt.summaryofslides.util.ImageTranscodeUtil
import com.lzt.summaryofslides.util.MarkdownHtmlUtil
import com.lzt.summaryofslides.util.MarkdownHtmlTemplate
import com.lzt.summaryofslides.util.MarkdownTidyUtil
import com.lzt.summaryofslides.util.NotificationUtil
import android.app.NotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

class AnalyzeEntryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryId = inputData.getString("entryId") ?: return Result.failure()
        val repo = AppContainer.entryRepository
        val settings = AppContainer.settingsStore.modelSettings.first()
        val llm = OpenAiCompatClient()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val extraPrompt = inputData.getString("extraPrompt")?.trim()?.takeIf { it.isNotBlank() }
        val batchImages = inputData.getBoolean("batchImages", false)
        val incremental = inputData.getBoolean("incremental", false)

        try {
            val baseUrl = settings.baseUrl.trim()
            val apiKey = settings.apiKey.trim()
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "FAILED",
                    stage = "PRECHECK",
                    current = null,
                    total = null,
                    message = "缺少 baseUrl / apiKey",
                    lastError = "Missing model baseUrl/apiKey",
                )
                return Result.failure()
            }

            val entry = repo.getEntry(entryId)
            val legacySlidesPdfPath = entry?.slidesPdfPath?.takeIf { it.isNotBlank() }
            val pdfs = repo.getEntryPdfs(entryId).ifEmpty {
                if (legacySlidesPdfPath != null) {
                    listOf(
                        EntryPdfEntity(
                            id = "legacy",
                            entryId = entryId,
                            createdAtEpochMs = System.currentTimeMillis(),
                            displayOrder = 1,
                            displayName = null,
                            localPath = legacySlidesPdfPath,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            val pdfFiles = pdfs.take(3).map { File(it.localPath) }
            val images = repo.getEntryImages(entryId)
            val hasImages = images.isNotEmpty()
            val hasPdfs = pdfFiles.isNotEmpty()
            if (settings.generalModel.isBlank()) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "FAILED",
                    stage = "PRECHECK",
                    current = null,
                    total = null,
                    message = "缺少通用模型",
                    lastError = "Missing generalModel",
                )
                return Result.failure()
            }
            if (!hasImages && !hasPdfs) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "FAILED",
                    stage = "PRECHECK",
                    current = 0,
                    total = 0,
                    message = "没有图片或PDF",
                    lastError = "No images or pdf",
                )
                return Result.failure()
            }
            if (hasImages && settings.visionModel.isBlank()) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "FAILED",
                    stage = "PRECHECK",
                    current = null,
                    total = null,
                    message = "缺少视觉模型",
                    lastError = "Missing visionModel",
                )
                return Result.failure()
            }

            if (!hasImages && hasPdfs) {
                repo.clearSlideAnalyses(entryId)
                val payload =
                    runCatching {
                        updateProgress(repo, entryId, "PROCESSING", "PARSE_PDF", null, null, "解析PDF为Markdown")
                        val pdfMd = parsePdfFilesToMarkdown(baseUrl, apiKey, pdfFiles)
                        updateProgress(repo, entryId, "PROCESSING", "CALL_GENERAL", null, null, "调用通用模型（基于PDF文本）")
                        val finalRaw =
                            llm.textChatCompletion(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = settings.generalModel,
                                prompt = withExtraPrompt(pdfMarkdownPrompt(pdfMd), extraPrompt),
                            )
                        parseSummaryPayload(json, finalRaw)
                    }.getOrElse { e ->
                        if (settings.visionModel.isBlank()) {
                            repo.updateEntryProgress(
                                entryId = entryId,
                                status = "FAILED",
                                stage = "PARSE_PDF",
                                current = null,
                                total = null,
                                message = "PDF解析失败，请配置视觉模型以回退解析",
                                lastError = e.message,
                            )
                            return Result.failure()
                        }

                        updateProgress(repo, entryId, "PROCESSING", "FALLBACK", null, null, "PDF解析失败，回退为逐页视觉解析")
                        val pageFiles = renderPdfToImageFiles(repo, entryId, pdfFiles)
                        val imagesForAnalysis =
                            pageFiles.mapIndexed { idx, file ->
                                ImageForAnalysis(
                                    entity =
                                        EntryImageEntity(
                                            id = "pdfpage-${idx + 1}",
                                            entryId = entryId,
                                            localPath = file.absolutePath,
                                            createdAtEpochMs = System.currentTimeMillis() + idx,
                                            pageIndex = idx + 1,
                                            displayOrder = idx + 1,
                                            displayName = null,
                                        ),
                                    jpegBytes = null,
                                )
                            }
                        analyzeImagesAndMerge(
                            repo = repo,
                            llm = llm,
                            json = json,
                            entryId = entryId,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            imagesForAnalysis = imagesForAnalysis,
                            visionModel = settings.visionModel,
                            generalModel = settings.generalModel,
                            mergeWithPdfs = false,
                            pdfFiles = emptyList(),
                            extraPrompt = extraPrompt,
                            batchImages = batchImages,
                            incrementalBaseSummary = null,
                        )
                    }
                val mdIndex = repo.getSummaryCount(entryId) + 1
                val mdFile = summaryMarkdownFile(repo, entryId, mdIndex, payload.shortTitle ?: payload.talkTitle)
                val htmlFile = summaryHtmlFile(repo, entryId, mdIndex, payload.shortTitle ?: payload.talkTitle)
                val cleaned = MarkdownTidyUtil.tidy(payload.finalSummaryMarkdown)
                writeUtf8(mdFile, cleaned)
                val html = MarkdownHtmlTemplate.wrap(MarkdownHtmlUtil.toHtml(cleaned))
                writeUtf8(htmlFile, html)
                repo.setFinalResult(
                    entryId = entryId,
                    shortTitle = payload.shortTitle,
                    speakerName = payload.speakerName,
                    speakerAffiliation = payload.speakerAffiliation,
                    talkTitle = payload.talkTitle,
                    keywords = payload.keywords,
                    finalSummary = cleaned,
                    summaryMdPath = mdFile.absolutePath,
                    summaryHtmlPath = htmlFile.absolutePath,
                )
                clearProgressNotification(entryId)
                return Result.success()
            }

            val imagesForAnalysis =
                images.map { ImageForAnalysis(it, jpegBytes = null) }

            updateProgress(repo, entryId, "PROCESSING", "PRECHECK", 0, imagesForAnalysis.size, "准备开始")
            if (!incremental) {
                repo.clearSlideAnalyses(entryId)
            }

            val baseSummary = if (incremental) repo.getEntry(entryId)?.finalSummary else null
            val payload =
                analyzeImagesAndMerge(
                    repo = repo,
                    llm = llm,
                    json = json,
                    entryId = entryId,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    imagesForAnalysis = imagesForAnalysis,
                    visionModel = settings.visionModel,
                    generalModel = settings.generalModel,
                    mergeWithPdfs = hasImages && hasPdfs,
                    pdfFiles = pdfFiles,
                    extraPrompt = extraPrompt,
                    batchImages = batchImages,
                    incrementalBaseSummary = baseSummary,
                )
            val shortTitle = payload.shortTitle
            val speakerName = payload.speakerName
            val speakerAffiliation = payload.speakerAffiliation
            val talkTitle = payload.talkTitle
            val keywords = payload.keywords
            val finalSummary = payload.finalSummaryMarkdown
            val slideNames = payload.slideNames

            for ((idx, img) in imagesForAnalysis.withIndex()) {
                val order = idx + 1
                val rawName = slideNames[order] ?: "第${order}页"
                val displayName = normalizeSlideName(rawName)
                repo.updateImageDisplay(img.entity.id, displayOrder = order, displayName = displayName)
                renameImageFileIfPossible(repo, entryId, img.entity.id, order, displayName)
            }

            updateProgress(repo, entryId, "PROCESSING", "GENERATE_FILE", null, null, "生成Markdown文件")
            val mdIndex = repo.getSummaryCount(entryId) + 1
            val mdFile = summaryMarkdownFile(repo, entryId, mdIndex, shortTitle ?: talkTitle)
            val htmlFile = summaryHtmlFile(repo, entryId, mdIndex, shortTitle ?: talkTitle)
            val cleaned = MarkdownTidyUtil.tidy(finalSummary)
            writeUtf8(mdFile, cleaned)
            val html = MarkdownHtmlTemplate.wrap(MarkdownHtmlUtil.toHtml(cleaned))
            writeUtf8(htmlFile, html)

            updateProgress(repo, entryId, "PROCESSING", "SAVE_RESULT", null, null, "保存结果")
            repo.setFinalResult(
                entryId = entryId,
                shortTitle = shortTitle,
                speakerName = speakerName,
                speakerAffiliation = speakerAffiliation,
                talkTitle = talkTitle,
                keywords = keywords,
                finalSummary = cleaned,
                summaryMdPath = mdFile.absolutePath,
                summaryHtmlPath = htmlFile.absolutePath,
            )

            clearProgressNotification(entryId)
            return Result.success()
        } catch (t: Throwable) {
            clearProgressNotification(entryId)
            val maxAttempts = 3
            val shouldRetry = isRetryable(t) && runAttemptCount < maxAttempts
            if (shouldRetry) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "QUEUED",
                    stage = "RETRY",
                    current = null,
                    total = null,
                    message = "失败，将重试（${runAttemptCount + 1}/$maxAttempts）",
                    lastError = t.message,
                )
                return Result.retry()
            }
            repo.updateEntryProgress(
                entryId = entryId,
                status = "FAILED",
                stage = "FAILED",
                current = null,
                total = null,
                message = "处理失败",
                lastError = t.message,
            )
            return Result.failure()
        }
    }

    private fun isRetryable(t: Throwable): Boolean {
        if (t is IOException) return true
        val cause = t.cause ?: return false
        return isRetryable(cause)
    }

    private data class SummaryPayload(
        val shortTitle: String?,
        val speakerName: String?,
        val speakerAffiliation: String?,
        val talkTitle: String?,
        val keywords: String?,
        val finalSummaryMarkdown: String,
        val slideNames: Map<Int, String>,
    )

    private fun parseSummaryPayload(json: Json, raw: String): SummaryPayload {
        val jsonString = extractJsonBlock(raw) ?: raw
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull()
        if (root != null) {
            val shortTitle = root["short_title"].stringOrNull()
            val speakerName = root["speaker_name"].stringOrNull()
            val speakerAffiliation = root["speaker_affiliation"].stringOrNull()
            val talkTitle = root["talk_title"].stringOrNull()
            val keywords = root["keywords"]?.jsonArray?.joinToString(",") { it.jsonPrimitive.content }
            val md = root["final_summary"].stringOrNull().orEmpty().ifBlank { extractMarkdownFallback(raw) }
            val slideNames =
                root["slide_names"]?.jsonArray
                    ?.mapNotNull { it.jsonObjectOrNull() }
                    ?.mapNotNull { obj ->
                        val page = obj["page_index"]?.intOrNull() ?: return@mapNotNull null
                        val name = obj["name"].stringOrNull() ?: return@mapNotNull null
                        page to sanitizeName(name)
                    }
                    ?.toMap()
                    ?: emptyMap()
            return SummaryPayload(shortTitle, speakerName, speakerAffiliation, talkTitle, keywords, md, slideNames)
        }

        val shortTitle = extractLooseJsonString(raw, "short_title")
        val speakerName = extractLooseJsonString(raw, "speaker_name")
        val speakerAffiliation = extractLooseJsonString(raw, "speaker_affiliation")
        val talkTitle = extractLooseJsonString(raw, "talk_title")
        val md = extractMarkdownFallback(raw)
        return SummaryPayload(shortTitle, speakerName, speakerAffiliation, talkTitle, null, md, emptyMap())
    }

    private suspend fun analyzeImagesAndMerge(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        llm: OpenAiCompatClient,
        json: Json,
        entryId: String,
        baseUrl: String,
        apiKey: String,
        imagesForAnalysis: List<ImageForAnalysis>,
        visionModel: String,
        generalModel: String,
        mergeWithPdfs: Boolean,
        pdfFiles: List<File>,
        extraPrompt: String?,
        batchImages: Boolean,
        incrementalBaseSummary: String?,
    ): SummaryPayload {
        val existing = repo.getSlideAnalyses(entryId)
        val existingIds = existing.map { it.imageId }.toSet()
        val newItems =
            imagesForAnalysis
                .mapIndexed { idx, img -> (idx + 1) to img }
                .filter { (_, img) -> !existingIds.contains(img.entity.id) }
        val newAnalyses = mutableListOf<SlideAnalysisEntity>()
        if (newItems.isNotEmpty()) {
            if (batchImages && newItems.size >= 2) {
                updateProgress(repo, entryId, "PROCESSING", "PREPARE_IMAGE", 0, newItems.size, "准备图片（批量）")
                val bytesList =
                    newItems.map { (_, img) ->
                        img.jpegBytes
                            ?: withContext(Dispatchers.IO) { ImageTranscodeUtil.loadAndCompressJpeg(File(img.entity.localPath)) }
                    }
                val prompt = withExtraPrompt(slidePromptBatch(newItems.map { it.first }), extraPrompt)
                updateProgress(repo, entryId, "PROCESSING", "CALL_VISION", 0, newItems.size, "调用视觉模型（批量）")
                val content =
                    llm.visionChatCompletionWithJpegBytesList(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = visionModel,
                        prompt = prompt,
                        jpegBytesList = bytesList,
                    )
                val parsed = parseBatchSlideJson(json, content)
                for ((idx, item) in parsed.withIndex()) {
                    val (page, img) = newItems.getOrNull(idx) ?: continue
                    newAnalyses +=
                        SlideAnalysisEntity(
                            id = UUID.randomUUID().toString(),
                            entryId = entryId,
                            imageId = img.entity.id,
                            extractedJson = item,
                            extractedText = null,
                            createdAtEpochMs = System.currentTimeMillis() + page,
                        )
                }
            } else {
                for ((page, img) in newItems) {
                    updateProgress(repo, entryId, "PROCESSING", "PREPARE_IMAGE", page, imagesForAnalysis.size, "第 $page 页：准备图片")
                    val bytes =
                        img.jpegBytes
                            ?: withContext(Dispatchers.IO) { ImageTranscodeUtil.loadAndCompressJpeg(File(img.entity.localPath)) }
                    val prompt = withExtraPrompt(slidePrompt(page), extraPrompt)
                    updateProgress(repo, entryId, "PROCESSING", "CALL_VISION", page, imagesForAnalysis.size, "第 $page 页：调用视觉模型")
                    val content =
                        llm.visionChatCompletion(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = visionModel,
                            prompt = prompt,
                            jpegBytes = bytes,
                        )
                    updateProgress(repo, entryId, "PROCESSING", "PARSE_VISION", page, imagesForAnalysis.size, "第 $page 页：保存解析结果")
                    newAnalyses +=
                        SlideAnalysisEntity(
                            id = UUID.randomUUID().toString(),
                            entryId = entryId,
                            imageId = img.entity.id,
                            extractedJson = content,
                            extractedText = null,
                            createdAtEpochMs = System.currentTimeMillis(),
                        )
                }
            }
            repo.saveSlideAnalyses(newAnalyses)
        }

        val allAnalyses = repo.getSlideAnalyses(entryId)
        val compactForMerge = StringBuilder()
        for ((idx, sa) in allAnalyses.withIndex()) {
            compactForMerge.append("Slide ").append(idx + 1).append(":\n").append(sa.extractedJson.orEmpty()).append("\n\n")
        }

        updateProgress(repo, entryId, "PROCESSING", "MERGE_SUMMARY", null, null, "融合信息与生成总结")
        val finalRaw =
            if (mergeWithPdfs) {
                updateProgress(repo, entryId, "PROCESSING", "PARSE_PDF", null, null, "解析PDF为Markdown")
                val pdfMd = runCatching { parsePdfFilesToMarkdown(baseUrl, apiKey, pdfFiles) }.getOrNull()
                if (pdfMd.isNullOrBlank()) {
                    updateProgress(repo, entryId, "PROCESSING", "FALLBACK", null, null, "PDF解析失败，回退为仅基于图片总结")
                    llm.textChatCompletion(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = generalModel,
                        prompt = withExtraPrompt(finalPrompt(compactForMerge.toString(), incrementalBaseSummary), extraPrompt),
                    )
                } else {
                    updateProgress(repo, entryId, "PROCESSING", "CALL_GENERAL", null, null, "调用通用模型（融合PDF文本）")
                    llm.textChatCompletion(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = generalModel,
                        prompt = withExtraPrompt(finalPromptWithPdfMarkdown(compactForMerge.toString(), pdfMd, incrementalBaseSummary), extraPrompt),
                    )
                }
            } else {
                llm.textChatCompletion(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = generalModel,
                    prompt = withExtraPrompt(finalPrompt(compactForMerge.toString(), incrementalBaseSummary), extraPrompt),
                )
            }
        return parseSummaryPayload(json, finalRaw)
    }

    private fun finalPromptWithPdfMarkdown(allSlides: String, pdfMd: String, baseSummary: String?): String {
        return """
你将收到同一份幻灯片材料的逐页JSON解析结果，同时还会收到由PDF版式解析器抽取出的Markdown内容。请综合两者，完成信息融合、延伸挖掘，并输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "slide_names": [{"page_index":1,"name":"封面"},{"page_index":2,"name":"目录"}],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX（行内用$...$，块级用$$...$$），不要用反引号或代码块包裹公式。为避免解析失败，不要使用Markdown表格（|---|），用标题+列表/段落表达。不要输出思维链，不要输出JSON以外的任何文本。"
}

上一次总结（如有）：
${baseSummary.orEmpty().take(40_000)}

逐页解析如下：
${allSlides.take(120_000)}

PDF抽取Markdown如下：
${pdfMd.take(120_000)}
""".trimIndent()
    }

    private fun finalPrompt(allSlides: String, baseSummary: String?): String {
        val base = baseSummary?.takeIf { it.isNotBlank() }?.take(40_000).orEmpty()
        if (base.isBlank()) return finalPrompt(allSlides)
        return """
你将收到同一份幻灯片材料的逐页JSON解析结果，以及上一次生成的总结内容。请在保持结构清晰的前提下，对总结进行增量修订：保留原有重要结构，融合新增信息，修正不准确之处。输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "slide_names": [{"page_index":1,"name":"封面"},{"page_index":2,"name":"目录"}],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX：行内公式用$...$且必须在同一行闭合（不要在$...$中换行），跨行/长公式用$$...$$。上下标必须使用大括号：x_{k}, x^{k}, x^{*}。不要用反引号或代码块包裹公式。为避免解析失败，不要使用Markdown表格（|---|），用标题+列表/段落表达。不要输出思维链，不要输出JSON以外的任何文本。"
}

上一次总结：
$base

逐页解析如下：
${allSlides.take(120_000)}
""".trimIndent()
    }

    private fun withExtraPrompt(prompt: String, extraPrompt: String?): String {
        if (extraPrompt.isNullOrBlank()) return prompt
        return prompt + "\n\n用户附加提示词：\n" + extraPrompt.take(4_000)
    }

    private fun slidePromptBatch(pages: List<Int>): String {
        val p = pages.joinToString(", ")
        return """
你将收到多张幻灯片图片（页码分别为：$p）。请按图片顺序输出严格JSON数组（不要包含除JSON以外的任何文本），数组每个元素结构与单页相同：
{
  "page_index": 1,
  "section": "本页所属章节/主题(可空)",
  "main_points": ["要点1","要点2"],
  "methods": ["方法/模型/算法(可空)"],
  "equations": ["公式/符号解释(可空)"],
  "figures": ["图表/示意图描述(可空)"],
  "citations": ["出现的论文/作者/会议期刊/DOI/链接线索(可空)"],
  "terms": ["术语及简短解释(可空)"],
  "raw_text": "尽可能完整的可读文本(可空)"
}
""".trimIndent()
    }

    private fun parseBatchSlideJson(json: Json, raw: String): List<String> {
        val block = extractJsonBlock(raw) ?: raw
        val el = runCatching { json.parseToJsonElement(block) }.getOrNull() ?: return emptyList()
        val arr = el as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.map { it.toString() }
    }

    private suspend fun renderPdfToImageFiles(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        pdfFiles: List<File>,
    ): List<File> {
        val pagesDir = repo.entryPdfPagesDir(entryId)
        pagesDir.deleteRecursively()
        pagesDir.mkdirs()
        val outFiles = mutableListOf<File>()
        val maxTotalPages = 60
        val totalPages = pdfFiles.sumOf { file -> countPdfPages(file) }.coerceAtLeast(1).coerceAtMost(maxTotalPages)
        var renderedCount = 0
        outer@ for ((pdfIdx, pdfFile) in pdfFiles.withIndex()) {
            if (!pdfFile.exists()) continue
            val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(fd)
                try {
                    val pageCount = renderer.pageCount
                    for (i in 0 until pageCount) {
                        if (renderedCount >= maxTotalPages) break@outer
                        renderedCount += 1
                        updateProgress(repo, entryId, "PROCESSING", "RENDER_PDF", renderedCount, totalPages, "渲染PDF（$renderedCount/$totalPages）")
                        val pageIndex = i + 1
                        val page = renderer.openPage(i)
                        val maxSide = 1280f
                        val scale =
                            (maxSide / maxOf(page.width, page.height).toFloat()).coerceAtMost(1f).coerceAtLeast(0.1f)
                        val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                        val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        val matrix = Matrix().apply { setScale(scale, scale) }
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        bitmap.recycle()
                        val bytes = baos.toByteArray()

                        val outFile = File(pagesDir, "pdf${pdfIdx + 1}_page_$pageIndex.jpg")
                        outFile.outputStream().use { it.write(bytes) }
                        outFiles += outFile
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                fd.close()
            }
        }
        return outFiles
    }

    private fun countPdfPages(file: File): Int {
        if (!file.exists()) return 0
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    private fun extractLooseJsonString(raw: String, key: String): String? {
        val r = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return r.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractMarkdownFallback(raw: String): String {
        val fenced =
            Regex("```(?:markdown|md)\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
                .find(raw)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
        if (!fenced.isNullOrBlank()) return fenced

        val startFrom = raw.indexOf("final_summary").takeIf { it >= 0 } ?: 0
        val segment = raw.substring(startFrom)
        val marker = Regex("(?m)^\\s*\"?#\\s+").find(segment)
        if (marker != null) {
            var md = segment.substring(marker.range.first).trim()
            if (md.startsWith("\"#")) md = md.drop(1)
            md = md.trimEnd()
            md = md.removeSuffix("\"")
            md = md.removeSuffix("}")
            md = md.removeSuffix("\"")
            return md.trim()
        }
        val hashIndex = segment.indexOf('#')
        if (hashIndex >= 0) {
            var md = segment.substring(hashIndex).trim()
            md = md.removeSuffix("\"")
            md = md.removeSuffix("}")
            md = md.removeSuffix("\"")
            return md.trim()
        }
        return raw.trim()
    }

    private data class ImageForAnalysis(
        val entity: EntryImageEntity,
        val jpegBytes: ByteArray?,
    )

    private fun slidePrompt(pageIndex: Int): String {
        return """
你是幻灯片内容解析助手。请阅读第 $pageIndex 张幻灯片图片，输出严格JSON（不要包含除JSON以外的任何文本），字段如下：
{
  "page_index": $pageIndex,
  "section": "本页所属章节/主题(可空)",
  "main_points": ["要点1","要点2"],
  "methods": ["方法/模型/算法(可空)"],
  "equations": ["公式/符号解释(可空)"],
  "figures": ["图表/示意图描述(可空)"],
  "citations": ["出现的论文/作者/会议期刊/DOI/链接线索(可空)"],
  "terms": ["术语及简短解释(可空)"],
  "raw_text": "尽可能完整的可读文本(可空)"
}
""".trimIndent()
    }

    private fun finalPrompt(allSlides: String): String {
        return """
你将收到同一份幻灯片材料的逐页JSON解析结果。请完成信息融合、延伸挖掘，并输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "slide_names": [{"page_index":1,"name":"封面"},{"page_index":2,"name":"目录"}],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX：行内公式用$...$且必须在同一行闭合（不要在$...$中换行），跨行/长公式用$$...$$。上下标必须使用大括号：x_{k}, x^{k}, x^{*}。不要用反引号或代码块包裹公式。为避免解析失败，不要使用Markdown表格（|---|），用标题+列表/段落表达。不要输出思维链，不要输出JSON以外的任何文本。（建议含：1.报告信息 2.整体摘要 3.逐页要点 4.关键术语/概念解释 5.相关工作延伸（列出可能的论文线索/链接） 6.开放问题与复现建议 7.给听众的下一步行动清单）"
}

逐页解析如下：
${allSlides.take(120_000)}
""".trimIndent()
    }

    private fun pdfOnlyPrompt(): String {
        return """
你将收到该条目对应的PDF文件（以PDF原始文件形式提供，不是图片）。请直接阅读PDF内容，完成信息提取、总结与延伸挖掘，并输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX：行内公式用$...$且必须在同一行闭合（不要在$...$中换行），跨行/长公式用$$...$$。上下标必须使用大括号：x_{k}, x^{k}, x^{*}。不要用反引号或代码块包裹公式。为避免解析失败，不要使用Markdown表格（|---|），用标题+列表/段落表达。不要输出思维链，不要输出JSON以外的任何文本。（建议含：1.报告信息 2.整体摘要 3.逐页要点 4.关键术语/概念解释 5.相关工作延伸 6.开放问题与复现建议 7.下一步行动清单）"
}
""".trimIndent()
    }

    private fun finalPromptWithPdfs(allSlides: String): String {
        return """
你将收到同一份幻灯片材料的逐页JSON解析结果，同时你还会收到该条目对应的PDF文件（以PDF原始文件形式提供，不是图片）。请综合JSON解析结果与PDF内容，完成信息融合、延伸挖掘，并输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "slide_names": [{"page_index":1,"name":"封面"},{"page_index":2,"name":"目录"}],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX：行内公式用$...$且必须在同一行闭合（不要在$...$中换行），跨行/长公式用$$...$$。上下标必须使用大括号：x_{k}, x^{k}, x^{*}。不要用反引号或代码块包裹公式。为避免解析失败，不要使用Markdown表格（|---|），用标题+列表/段落表达。不要输出思维链，不要输出JSON以外的任何文本。（建议含：1.报告信息 2.整体摘要 3.逐页要点 4.关键术语/概念解释 5.相关工作延伸（列出可能的论文线索/链接） 6.开放问题与复现建议 7.给听众的下一步行动清单）"
}

逐页解析如下：
${allSlides.take(120_000)}
""".trimIndent()
    }

    private fun extractJsonBlock(text: String): String? {
        val fenced = Regex("```json\\s*([\\s\\S]*?)\\s*```").find(text)?.groupValues?.getOrNull(1)
        if (!fenced.isNullOrBlank()) return fenced.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return null
    }

    private fun JsonElement?.stringOrNull(): String? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content
    }

    private fun JsonElement?.intOrNull(): Int? {
        val p = this as? JsonPrimitive ?: return null
        return p.intOrNull
    }

    private fun JsonElement?.jsonObjectOrNull() =
        runCatching { this?.jsonObject }.getOrNull()

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("""[\\/:*?"<>|]"""), " ")
        return cleaned.replace(Regex("""\s+"""), " ").trim().take(40).ifBlank { "未命名" }
    }

    private suspend fun parsePdfFilesToMarkdown(baseUrl: String, apiKey: String, pdfFiles: List<File>): String {
        val lower = baseUrl.lowercase()
        val isZhiPu = lower.contains("/api/paas/v4") || lower.contains("/api/coding/paas/v4")
        if (!isZhiPu) throw IllegalStateException("当前供应商不支持PDF解析")
        val sb = StringBuilder()
        for ((idx, file) in pdfFiles.withIndex()) {
            val current = idx + 1
            val parser = ZhiPuDocParser()
            val md =
                runCatching {
                    parser.parsePdfToMarkdown(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        pdfFile = file,
                        startPageId = 1,
                        endPageId = 50,
                    )
                }.recoverCatching {
                    parser.parsePdfToMarkdown(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        pdfFile = file,
                        startPageId = 1,
                        endPageId = 20,
                    )
                }.recoverCatching {
                    parser.parsePdfToMarkdown(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        pdfFile = file,
                        startPageId = 1,
                        endPageId = 10,
                    )
                }.getOrThrow()
            sb.append("\n\n").append("## Document ").append(current).append("\n\n").append(md)
        }
        return sb.toString().trim()
    }

    private fun pdfMarkdownPrompt(md: String): String {
        return """
你将收到一份由PDF版式解析器抽取出的Markdown内容（可能包含公式、表格、章节结构）。请只基于该内容进行学术报告/幻灯片材料总结与延伸挖掘，并输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX（行内用$...$，块级用$$...$$），不要用反引号或代码块包裹公式。为避免解析失败，不要使用Markdown表格（|---|），用标题+列表/段落表达。不要输出思维链，不要输出JSON以外的任何文本。"
}

文档Markdown如下：
${md.take(120_000)}
""".trimIndent()
    }

    private fun summaryMarkdownFile(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        index: Int,
        title: String?,
    ): File {
        val base = sanitizeFileStem(title ?: "summary")
        val prefix = "S$index-"
        return File(repo.entryDir(entryId), "$prefix$base.md")
    }

    private fun summaryHtmlFile(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        index: Int,
        title: String?,
    ): File {
        val base = sanitizeFileStem(title ?: "summary")
        val prefix = "H$index-"
        return File(repo.entryDir(entryId), "$prefix$base.html")
    }

    private fun sanitizeFileStem(raw: String): String {
        return raw.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(80)
            .ifBlank { "summary" }
    }

    private fun normalizeSlideName(raw: String): String {
        return raw.trim()
            .replace(Regex("""[\r\n]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(24)
            .ifBlank { "未命名" }
    }

    private suspend fun renameImageFileIfPossible(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        imageId: String,
        order: Int,
        displayName: String,
    ) {
        val imagesDir = repo.entryImagesDir(entryId)
        val current = File(imagesDir, "$imageId.jpg")
        if (!current.exists()) return
        val stem = sanitizeFileStem(displayName).take(40)
        val target = File(imagesDir, "${order}-${stem}.jpg")
        val out =
            if (target.exists() && target.absolutePath != current.absolutePath) {
                File(imagesDir, "${order}-${stem}-${imageId.take(4)}.jpg")
            } else {
                target
            }
        if (out.absolutePath == current.absolutePath) return
        val ok = runCatching { current.renameTo(out) }.getOrDefault(false)
        if (ok) {
            repo.updateImageLocalPath(imageId, out.absolutePath)
        }
    }

    private suspend fun writeUtf8(file: File, text: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(text, Charsets.UTF_8)
        }
    }

    private suspend fun updateProgress(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        status: String,
        stage: String?,
        current: Int?,
        total: Int?,
        message: String?,
    ) {
        repo.updateEntryProgress(entryId, status, stage, current, total, message, null)
        postProgressNotification(entryId, stage, current, total, message)
    }

    private fun postProgressNotification(
        entryId: String,
        stage: String?,
        current: Int?,
        total: Int?,
        message: String?,
    ) {
        NotificationUtil.ensureChannel(applicationContext)
        val title = "Summary of Slides"
        val text =
            when {
                current != null && total != null && message != null -> "$message（$current/$total）"
                message != null -> message
                stage != null -> stage
                else -> "处理中"
            }
        val notification =
            NotificationUtil.buildProgressNotification(
                context = applicationContext,
                title = title,
                text = text,
            ).build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(entryId.hashCode(), notification) }
    }

    private fun clearProgressNotification(entryId: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.cancel(entryId.hashCode()) }
    }
}
