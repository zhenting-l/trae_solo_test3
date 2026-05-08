package com.lzt.summaryofslides.ui.share

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryEntity
import com.lzt.summaryofslides.data.db.EntryImageEntity
import com.lzt.summaryofslides.data.db.EntryPdfEntity
import com.lzt.summaryofslides.data.db.EntrySummaryEntity
import com.lzt.summaryofslides.util.MarkdownHtmlTemplate
import com.lzt.summaryofslides.util.MarkdownHtmlUtil
import com.lzt.summaryofslides.util.MarkdownTidyUtil
import com.lzt.summaryofslides.util.ShareUtil
import com.lzt.summaryofslides.util.ZipUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ShareViewModel(private val entryId: String) : ViewModel() {
    private val repo = AppContainer.entryRepository

    val entry: StateFlow<EntryEntity?> =
        repo.observeEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val images: StateFlow<List<EntryImageEntity>> =
        repo.observeEntryImages(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pdfs: StateFlow<List<EntryPdfEntity>> =
        repo.observeEntryPdfs(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summaries: StateFlow<List<EntrySummaryEntity>> =
        repo.observeSummaries(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ensureSummaryHtmlFiles() {
        viewModelScope.launch {
            val list = summaries.value
            for (s in list) {
                if (!s.summaryHtmlPath.isNullOrBlank()) continue
                val mdPath = s.summaryMdPath ?: continue
                val mdFile = File(mdPath)
                if (!mdFile.exists()) continue
                val dir = mdFile.parentFile ?: repo.entryDir(entryId)
                val htmlName = mdToHtmlName(mdFile.name)
                val htmlFile = File(dir, htmlName)
                if (!htmlFile.exists()) {
                    val md = runCatching { mdFile.readText(Charsets.UTF_8) }.getOrNull() ?: continue
                    val cleaned = MarkdownTidyUtil.tidy(md)
                    val html = MarkdownHtmlTemplate.wrap(MarkdownHtmlUtil.toHtml(cleaned))
                    runCatching { htmlFile.writeText(html, Charsets.UTF_8) }
                }
                if (htmlFile.exists()) {
                    repo.updateSummaryHtmlPath(s.id, htmlFile.absolutePath)
                }
            }
        }
    }

    fun share(
        context: Context,
        selectedSummaryMdIds: Set<String>,
        selectedSummaryHtmlIds: Set<String>,
        selectedUploadKeys: Set<String>,
    ) {
        viewModelScope.launch {
            val files = mutableListOf<Pair<File, String>>()
            val chosenMdSummaries = summaries.value.filter { selectedSummaryMdIds.contains(it.id) }
            for (s in chosenMdSummaries) {
                val mdPath = s.summaryMdPath ?: continue
                val file = File(mdPath)
                if (!file.exists()) continue
                files += file to "summaries_md/${file.name}"
            }

            val chosenHtmlSummaries = summaries.value.filter { selectedSummaryHtmlIds.contains(it.id) }
            for (s in chosenHtmlSummaries) {
                val htmlPath = s.summaryHtmlPath ?: continue
                val file = File(htmlPath)
                if (!file.exists()) continue
                files += file to "summaries_html/${file.name}"
            }

            val chosenImages =
                images.value.filter { selectedUploadKeys.contains("img:${it.id}") }
            for ((idx, img) in chosenImages.withIndex()) {
                val order = img.displayOrder ?: (idx + 1)
                val name = img.displayName?.ifBlank { null } ?: "图片$order"
                val fileName = "${order}-${sanitizeFileName(name)}.jpg"
                files += File(img.localPath) to "uploads/$fileName"
            }

            val chosenPdfs =
                pdfs.value.filter { selectedUploadKeys.contains("pdf:${it.id}") }
            for (pdf in chosenPdfs) {
                val rawName = pdf.displayName?.ifBlank { null } ?: "slides_${pdf.displayOrder}.pdf"
                val fileName = sanitizeFileName(rawName)
                files += File(pdf.localPath) to "uploads/$fileName"
            }

            if (files.isEmpty()) return@launch

            if (files.size == 1) {
                val (file, name) = files.first()
                val mimeType =
                    when {
                        name.endsWith(".pdf") -> "application/pdf"
                        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                        name.endsWith(".md") -> "text/markdown"
                        name.endsWith(".html") || name.endsWith(".htm") -> "text/html"
                        else -> "application/octet-stream"
                    }
                ShareUtil.shareSingleFile(context, file, mimeType)
            } else {
                val chosenSummaries = chosenMdSummaries + chosenHtmlSummaries
                val baseTitle =
                    (chosenSummaries.singleOrNull()?.shortTitle
                        ?: chosenSummaries.singleOrNull()?.talkTitle
                        ?: entry.value?.shortTitle
                        ?: entry.value?.title
                        ?: entry.value?.talkTitle
                        ?: "summary")
                val baseStem = sanitizeFileName(baseTitle)
                val out =
                    File(context.cacheDir, "share/${baseStem}-${System.currentTimeMillis()}.zip").apply {
                        parentFile?.mkdirs()
                    }
                ZipUtil.zipFiles(files, out)
                ShareUtil.shareZip(context, out)
            }
        }
    }
}

class ShareViewModelFactory(private val entryId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ShareViewModel(entryId) as T
    }
}

private fun mdToHtmlName(mdName: String): String {
    val m = Regex("""^S(\d+)-(.*)\.md$""").find(mdName)
    if (m != null) {
        val idx = m.groupValues[1]
        val rest = m.groupValues[2]
        return "H$idx-$rest.html"
    }
    val stem = mdName.removeSuffix(".md")
    return "H-$stem.html"
}

private fun sanitizeFileName(raw: String): String {
    return raw.trim()
        .replace(Regex("""[\\/:*?"<>|]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(60)
        .ifBlank { "未命名" }
}
