package com.lzt.summaryofslides.ui.share

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryEntity
import com.lzt.summaryofslides.data.db.EntryImageEntity
import com.lzt.summaryofslides.data.db.EntryPdfEntity
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

    fun share(
        context: Context,
        selectedImageIds: Set<String>,
        includeSummaryMd: Boolean,
        includeSlidesPdf: Boolean,
    ) {
        viewModelScope.launch {
            val files = mutableListOf<Pair<File, String>>()
            val chosenImages = images.value.filter { selectedImageIds.contains(it.id) }
            for ((idx, img) in chosenImages.withIndex()) {
                val order = img.displayOrder ?: (idx + 1)
                val name = img.displayName?.ifBlank { null } ?: "图片$order"
                val fileName = "${order}-${sanitizeFileName(name)}.jpg"
                files += File(img.localPath) to "images/$fileName"
            }

            val summaryPath = entry.value?.summaryMdPath
            val baseTitle = entry.value?.shortTitle ?: entry.value?.title ?: entry.value?.talkTitle ?: "summary"
            val baseStem = sanitizeFileName(baseTitle)
            if (includeSummaryMd && !summaryPath.isNullOrBlank()) {
                files += File(summaryPath) to "$baseStem.md"
            }

            if (includeSlidesPdf) {
                for (pdf in pdfs.value) {
                    files += File(pdf.localPath) to "slides_${pdf.displayOrder}.pdf"
                }
            }

            if (files.isEmpty()) return@launch

            if (files.size == 1) {
                val (file, name) = files.first()
                val mimeType =
                    when {
                        name.endsWith(".pdf") -> "application/pdf"
                        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                        name.endsWith(".md") -> "text/markdown"
                        else -> "application/octet-stream"
                    }
                ShareUtil.shareSingleFile(context, file, mimeType)
            } else {
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

private fun sanitizeFileName(raw: String): String {
    return raw.trim()
        .replace(Regex("""[\\/:*?"<>|]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(60)
        .ifBlank { "未命名" }
}
