package com.lzt.summaryofslides.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryImageEntity
import com.lzt.summaryofslides.data.db.EntryPdfEntity
import com.lzt.summaryofslides.data.db.SlideAnalysisEntity
import com.lzt.summaryofslides.llm.OpenAiCompatClient
import com.lzt.summaryofslides.llm.ZhiPuDocParser
import com.lzt.summaryofslides.util.ImageTranscodeUtil
import com.lzt.summaryofslides.util.MarkdownPdfUtil
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
        val pdfMode = inputData.getString("pdfMode")
        val repo = AppContainer.entryRepository
        val settings = AppContainer.settingsStore.modelSettings.first()
        val llm = OpenAiCompatClient()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
            if (hasImages && hasPdfs && settings.generalModel.isBlank()) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "FAILED",
                    stage = "PRECHECK",
                    current = null,
                    total = null,
                    message = "缺少通用模型（图片+PDF）",
                    lastError = "Missing generalModel",
                )
                return Result.failure()
            }
            if ((hasImages && !hasPdfs) || (!hasImages && hasPdfs && pdfMode != "direct")) {
                if (settings.visionModel.isBlank()) {
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
                if (settings.textModel.isBlank()) {
                    repo.updateEntryProgress(
                        entryId = entryId,
                        status = "FAILED",
                        stage = "PRECHECK",
                        current = null,
                        total = null,
                        message = "缺少文本模型",
                        lastError = "Missing textModel",
                    )
                    return Result.failure()
                }
            }
            if (!hasImages && hasPdfs && pdfMode == "direct" && settings.textModel.isBlank()) {
                repo.updateEntryProgress(
                    entryId = entryId,
                    status = "FAILED",
                    stage = "PRECHECK",
                    current = null,
                    total = null,
                    message = "缺少文本模型",
                    lastError = "Missing textModel",
                )
                return Result.failure()
            }

            val imagesForAnalysis =
                when {
                    hasImages && hasPdfs -> {
                        images.map { ImageForAnalysis(it, jpegBytes = null) }
                    }
                    !hasImages && hasPdfs -> {
                        if (pdfMode == "direct") {
                            val directOk =
                                runCatching { analyzePdfDirect(repo, entryId, pdfFiles, baseUrl, apiKey, settings.textModel) }.getOrNull()
                            if (directOk == true) {
                                clearProgressNotification(entryId)
                                return Result.success()
                            }
                            updateProgress(repo, entryId, "PROCESSING", "FALLBACK", null, null, "PDF直读失败，回退为逐页视觉解析")
                        }
                        renderPdfToImages(repo, entryId, pdfFiles, startOrder = 0)
                    }
                    hasImages -> {
                        images.map { ImageForAnalysis(it, jpegBytes = null) }
                    }
                    else -> {
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
                }

            updateProgress(repo, entryId, "PROCESSING", "PRECHECK", 0, imagesForAnalysis.size, "准备开始")
            repo.clearSlideAnalyses(entryId)

            val slideAnalyses = mutableListOf<SlideAnalysisEntity>()
            val compactForMerge = StringBuilder()
            val visionModelForAnalysis =
                if (hasImages && hasPdfs) settings.generalModel else settings.visionModel
            for ((idx, img) in imagesForAnalysis.withIndex()) {
                val page = idx + 1
                updateProgress(repo, entryId, "PROCESSING", "PREPARE_IMAGE", page, imagesForAnalysis.size, "第 $page 页：准备图片")
                val bytes =
                    img.jpegBytes
                        ?: withContext(Dispatchers.IO) {
                            ImageTranscodeUtil.loadAndCompressJpeg(File(img.entity.localPath))
                        }
                val prompt = slidePrompt(page)
                updateProgress(repo, entryId, "PROCESSING", "CALL_VISION", page, imagesForAnalysis.size, "第 $page 页：调用视觉模型")
                val content =
                    llm.visionChatCompletion(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = visionModelForAnalysis,
                        prompt = prompt,
                        jpegBytes = bytes,
                    )
                updateProgress(repo, entryId, "PROCESSING", "PARSE_VISION", page, imagesForAnalysis.size, "第 $page 页：保存解析结果")
                slideAnalyses +=
                    SlideAnalysisEntity(
                        id = UUID.randomUUID().toString(),
                        entryId = entryId,
                        imageId = img.entity.id,
                        extractedJson = content,
                        extractedText = null,
                        createdAtEpochMs = System.currentTimeMillis(),
                    )
                compactForMerge.append("Slide ").append(page).append(":\n").append(content).append("\n\n")
            }

            repo.saveSlideAnalyses(slideAnalyses)

            updateProgress(repo, entryId, "PROCESSING", "MERGE_SUMMARY", null, null, "融合信息与生成总结")
            val finalRaw =
                if (hasImages && hasPdfs) {
                    updateProgress(repo, entryId, "PROCESSING", "CALL_GENERAL", null, null, "调用通用模型（PDF直传）")
                    runCatching {
                        llm.visionChatCompletionWithPdfFiles(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = settings.generalModel,
                            prompt = finalPromptWithPdfs(compactForMerge.toString()),
                            pdfFiles = pdfFiles,
                        )
                    }.getOrElse { e ->
                        repo.updateEntryProgress(
                            entryId = entryId,
                            status = "FAILED",
                            stage = "CALL_GENERAL",
                            current = null,
                            total = null,
                            message = "通用模型不支持PDF直传或PDF过大",
                            lastError = e.message,
                        )
                        return Result.failure()
                    }
                } else {
                    if (settings.textModel.isBlank()) {
                        repo.updateEntryProgress(
                            entryId = entryId,
                            status = "FAILED",
                            stage = "PRECHECK",
                            current = null,
                            total = null,
                            message = "缺少文本模型",
                            lastError = "Missing textModel",
                        )
                        return Result.failure()
                    }
                    llm.textChatCompletion(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = settings.textModel,
                        prompt = finalPrompt(compactForMerge.toString()),
                    )
                }

            val payload = parseSummaryPayload(json, finalRaw)
            val shortTitle = payload.shortTitle
            val speakerName = payload.speakerName
            val speakerAffiliation = payload.speakerAffiliation
            val talkTitle = payload.talkTitle
            val keywords = payload.keywords
            val finalSummary = payload.finalSummaryMarkdown
            val slideNames = payload.slideNames

            for ((idx, img) in imagesForAnalysis.withIndex()) {
                val order = idx + 1
                val displayName = slideNames[order] ?: "第${order}页"
                repo.updateImageDisplay(img.entity.id, displayOrder = order, displayName = displayName)
            }

            updateProgress(repo, entryId, "PROCESSING", "GENERATE_PDF", null, null, "生成PDF")
            val pdfFile = File(repo.entryDir(entryId), "summary_${System.currentTimeMillis()}.pdf")
            MarkdownPdfUtil.writeMarkdownPdf(applicationContext, finalSummary, pdfFile)

            updateProgress(repo, entryId, "PROCESSING", "SAVE_RESULT", null, null, "保存结果")
            repo.setFinalResult(
                entryId = entryId,
                shortTitle = shortTitle,
                speakerName = speakerName,
                speakerAffiliation = speakerAffiliation,
                talkTitle = talkTitle,
                keywords = keywords,
                finalSummary = finalSummary,
                summaryPdfPath = pdfFile.absolutePath,
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

    private suspend fun analyzePdfDirect(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        pdfFiles: List<File>,
        baseUrl: String,
        apiKey: String,
        textModel: String,
    ): Boolean {
        val maxBytes = 15L * 1024L * 1024L
        for (file in pdfFiles) {
            if (file.exists() && file.length() > maxBytes) {
                throw IllegalStateException("PDF过大，已自动回退为逐页视觉解析")
            }
        }
        val mdAll = StringBuilder()
        for ((idx, file) in pdfFiles.withIndex()) {
            val current = idx + 1
            updateProgress(repo, entryId, "PROCESSING", "PDF_DIRECT", current, pdfFiles.size, "PDF直读：解析文档 $current/${pdfFiles.size}")
            val md =
                ZhiPuDocParser().parsePdfToMarkdown(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    pdfFile = file,
                    startPageId = 1,
                    endPageId = 50,
                )
            mdAll.append("\n\n").append("## Document ").append(current).append("\n\n").append(md)
        }
        updateProgress(repo, entryId, "PROCESSING", "PDF_DIRECT", null, null, "PDF直读：生成总结")
        val finalRaw =
            OpenAiCompatClient().textChatCompletion(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = textModel,
                prompt = finalPromptFromMarkdown(mdAll.toString()),
            )
        val payload = parseSummaryPayload(Json { ignoreUnknownKeys = true; isLenient = true }, finalRaw)

        val shortTitle = payload.shortTitle
        val speakerName = payload.speakerName
        val speakerAffiliation = payload.speakerAffiliation
        val talkTitle = payload.talkTitle
        val keywords = payload.keywords
        val finalSummary = payload.finalSummaryMarkdown

        updateProgress(repo, entryId, "PROCESSING", "GENERATE_PDF", null, null, "生成PDF")
        val pdfOut = File(repo.entryDir(entryId), "summary_${System.currentTimeMillis()}.pdf")
        MarkdownPdfUtil.writeMarkdownPdf(applicationContext, finalSummary, pdfOut)

        updateProgress(repo, entryId, "PROCESSING", "SAVE_RESULT", null, null, "保存结果")
        repo.setFinalResult(
            entryId = entryId,
            shortTitle = shortTitle,
            speakerName = speakerName,
            speakerAffiliation = speakerAffiliation,
            talkTitle = talkTitle,
            keywords = keywords,
            finalSummary = finalSummary,
            summaryPdfPath = pdfOut.absolutePath,
        )
        clearProgressNotification(entryId)
        return true
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

    private suspend fun renderPdfToImages(
        repo: com.lzt.summaryofslides.data.repo.EntryRepository,
        entryId: String,
        pdfFiles: List<File>,
        startOrder: Int,
    ): List<ImageForAnalysis> {
        val pagesDir = repo.entryPdfPagesDir(entryId)
        pagesDir.deleteRecursively()
        pagesDir.mkdirs()
        val now = System.currentTimeMillis()
        val entities = mutableListOf<EntryImageEntity>()
        val result = mutableListOf<ImageForAnalysis>()
        val maxTotalPages = 80
        val totalPages = pdfFiles.sumOf { file -> countPdfPages(file) }.coerceAtLeast(1).coerceAtMost(maxTotalPages)

        var globalOrder = startOrder
        var renderedCount = 0
        outer@ for ((pdfIdx, pdfFile) in pdfFiles.withIndex()) {
            if (!pdfFile.exists()) throw IllegalStateException("slides pdf not found")
            val dir = File(pagesDir, "pdf_${pdfIdx + 1}").apply { mkdirs() }
            val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(fd)
                try {
                    val pageCount = renderer.pageCount
                    for (i in 0 until pageCount) {
                        if (renderedCount >= maxTotalPages) break@outer
                        val pageIndex = i + 1
                        renderedCount += 1
                        globalOrder += 1
                        updateProgress(repo, entryId, "PROCESSING", "RENDER_PDF", renderedCount, totalPages, "渲染PDF（$renderedCount/$totalPages）")
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
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                        bitmap.recycle()
                        val bytes = baos.toByteArray()

                        val outFile = File(dir, "page_$pageIndex.jpg")
                        outFile.outputStream().use { it.write(bytes) }

                        val imageId = "pdf${pdfIdx + 1}-$pageIndex"
                        val entity =
                            EntryImageEntity(
                                id = imageId,
                                entryId = entryId,
                                localPath = outFile.absolutePath,
                                createdAtEpochMs = now + globalOrder,
                                pageIndex = pageIndex,
                                displayOrder = globalOrder,
                                displayName = null,
                            )
                        entities += entity
                        result += ImageForAnalysis(entity, bytes)
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                fd.close()
            }
        }
        repo.upsertEntryImages(entities)
        return result
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
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX（行内用$...$，块级用$$...$$）。不要输出思维链，不要输出JSON以外的任何文本。（建议含：1.报告信息 2.整体摘要 3.逐页要点 4.关键术语/概念解释 5.相关工作延伸（列出可能的论文线索/链接） 6.开放问题与复现建议 7.给听众的下一步行动清单）"
}

逐页解析如下：
${allSlides.take(120_000)}
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
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX（行内用$...$，块级用$$...$$）。不要输出思维链，不要输出JSON以外的任何文本。（建议含：1.报告信息 2.整体摘要 3.逐页要点 4.关键术语/概念解释 5.相关工作延伸（列出可能的论文线索/链接） 6.开放问题与复现建议 7.给听众的下一步行动清单）"
}

逐页解析如下：
${allSlides.take(120_000)}
""".trimIndent()
    }

    private fun finalPromptFromMarkdown(md: String): String {
        return """
你将收到一份由文档解析工具抽取出的Markdown内容（可能包含公式、表格、图片占位、章节结构）。请对其进行幻灯片内容总结与延伸挖掘，并输出严格JSON（不要包含除JSON以外的任何文本），结构如下：
{
  "short_title": "为该条目生成一个最适合展示在列表页的短标题（1-3行，可中英文混用，尽量信息密度高）",
  "speaker_name": "报告人姓名(若无法确定则空字符串)",
  "speaker_affiliation": "单位/机构(若无法确定则空字符串)",
  "talk_title": "报告标题(若无法确定则空字符串)",
  "keywords": ["关键词1","关键词2"],
  "final_summary": "详版总结正文（Markdown）。final_summary 必须是合法JSON字符串：换行请用 \\n，双引号请用 \\\"。数学公式使用LaTeX（行内用$...$，块级用$$...$$）。不要输出思维链，不要输出JSON以外的任何文本。"
}

文档Markdown如下（可能很长，请只基于此内容，不要凭空编造）：
${md.take(120_000)}
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
