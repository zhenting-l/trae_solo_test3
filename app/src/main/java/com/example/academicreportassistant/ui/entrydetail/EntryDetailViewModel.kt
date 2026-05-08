package com.example.academicreportassistant.ui.entrydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.academicreportassistant.data.AppContainer
import com.example.academicreportassistant.data.db.EntryEntity
import com.example.academicreportassistant.data.db.EntryImageEntity
import com.example.academicreportassistant.data.db.EntryPdfEntity
import com.example.academicreportassistant.data.db.SlideAnalysisEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.academicreportassistant.worker.WorkEnqueuer
import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.first

class EntryDetailViewModel(private val entryId: String) : ViewModel() {
    private val repo = AppContainer.entryRepository

    val entry: StateFlow<EntryEntity?> =
        repo.observeEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val images: StateFlow<List<EntryImageEntity>> =
        repo.observeEntryImages(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pdfs: StateFlow<List<EntryPdfEntity>> =
        repo.observeEntryPdfs(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analyses: StateFlow<List<SlideAnalysisEntity>> =
        repo.observeSlideAnalyses(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summaryCount: StateFlow<Int> =
        repo.observeSummaryCount(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun importGalleryUris(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                repo.importImageFromUri(entryId, uri)
            }
        }
    }

    fun importCapturedFile(tempFile: File, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.importImageFromFile(entryId, tempFile)
            tempFile.delete()
            onDone()
        }
    }

    fun importSlidesPdf(uri: Uri) {
        viewModelScope.launch {
            runCatching { repo.importSlidesPdfFromUri(entryId, uri) }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun deleteImage(imageId: String) {
        viewModelScope.launch {
            runCatching { repo.deleteEntryImage(entryId, imageId) }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun deletePdf(pdfId: String) {
        viewModelScope.launch {
            runCatching { repo.deleteEntryPdf(entryId, pdfId) }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun startAnalysis(context: Context) {
        viewModelScope.launch {
            val settings = AppContainer.settingsStore.modelSettings.first()
            if (settings.baseUrl.isBlank() || settings.apiKey.isBlank()) {
                _errorMessage.value = "缺少 baseUrl / apiKey"
                return@launch
            }
            val hasVision = settings.visionModel.isNotBlank() || settings.textModel.isNotBlank()
            if (!hasVision) {
                _errorMessage.value = "缺少视觉模型（或至少填写文本模型作为回退）"
                return@launch
            }
            val images = repo.getEntryImages(entryId)
            val pdfs = repo.getEntryPdfs(entryId)
            val hasImages = images.isNotEmpty()
            val hasPdfs = pdfs.isNotEmpty()
            if (!hasImages && hasPdfs) {
                _errorMessage.value = "仅有PDF时请使用“开始分析PDF并生成总结”并选择分析方式"
                return@launch
            }
            val total = images.size
            repo.updateEntryProgress(
                entryId = entryId,
                status = "QUEUED",
                stage = "QUEUED",
                current = 0,
                total = total,
                message = "已加入队列",
                lastError = null,
            )
            WorkEnqueuer.enqueueAnalyzeEntry(context, entryId, source = "auto", pdfMode = if (hasPdfs) "vision" else null)
        }
    }

    fun startPdfAnalysis(context: Context, pdfMode: String) {
        viewModelScope.launch {
            val settings = AppContainer.settingsStore.modelSettings.first()
            if (settings.baseUrl.isBlank() || settings.apiKey.isBlank()) {
                _errorMessage.value = "缺少 baseUrl / apiKey"
                return@launch
            }
            if (pdfMode == "direct") {
                if (settings.textModel.isBlank()) {
                    _errorMessage.value = "缺少文本模型"
                    return@launch
                }
            } else {
                val hasVision = settings.visionModel.isNotBlank() || settings.textModel.isNotBlank()
                if (!hasVision) {
                    _errorMessage.value = "缺少视觉模型（或至少填写文本模型作为回退）"
                    return@launch
                }
            }
            repo.updateEntryProgress(
                entryId = entryId,
                status = "QUEUED",
                stage = "QUEUED",
                current = 0,
                total = null,
                message = "PDF已加入队列",
                lastError = null,
            )
            WorkEnqueuer.enqueueAnalyzeEntry(context, entryId, source = "auto", pdfMode = pdfMode)
        }
    }

    fun cancelAnalysis(context: Context) {
        viewModelScope.launch {
            WorkEnqueuer.cancelAnalyzeEntry(context, entryId)
            repo.updateEntryProgress(
                entryId = entryId,
                status = "CANCELLED",
                stage = "CANCELLED",
                current = null,
                total = null,
                message = "已终止",
                lastError = null,
            )
        }
    }
}

class EntryDetailViewModelFactory(private val entryId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EntryDetailViewModel(entryId) as T
    }
}
