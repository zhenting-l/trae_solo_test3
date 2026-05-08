package com.lzt.summaryofslides.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lzt.summaryofslides.data.db.AppDatabase
import com.lzt.summaryofslides.data.db.EntryEntity
import com.lzt.summaryofslides.data.db.EntryImageEntity
import com.lzt.summaryofslides.data.db.EntryPdfEntity
import com.lzt.summaryofslides.data.db.EntrySummaryEntity
import com.lzt.summaryofslides.data.db.SlideAnalysisEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class EntryRepository(
    private val db: AppDatabase,
    private val appContext: Context,
) {
    companion object {
        const val MaxPdfsPerEntry = 3
        const val PdfLimitMessage = "当前条目PDF文件过多，请新建条目后导入"
        const val MaxEntryTitleChars = 24
    }

    fun observeEntries(): Flow<List<EntryEntity>> = db.entryDao().observeAll()

    fun observeEntry(entryId: String): Flow<EntryEntity?> = db.entryDao().observeById(entryId)

    suspend fun getEntry(entryId: String): EntryEntity? = db.entryDao().getById(entryId)

    fun observeEntryImages(entryId: String): Flow<List<EntryImageEntity>> =
        db.entryImageDao().observeByEntry(entryId)

    fun observeSlideAnalyses(entryId: String): Flow<List<SlideAnalysisEntity>> =
        db.slideAnalysisDao().observeByEntry(entryId)

    fun observeEntryPdfs(entryId: String): Flow<List<EntryPdfEntity>> =
        db.entryPdfDao().observeByEntry(entryId)

    suspend fun getEntryPdfs(entryId: String): List<EntryPdfEntity> =
        db.entryPdfDao().getByEntry(entryId)

    fun observeSummaries(entryId: String): Flow<List<EntrySummaryEntity>> =
        db.entrySummaryDao().observeByEntry(entryId)

    suspend fun getSummaryCount(entryId: String): Int =
        db.entrySummaryDao().countByEntry(entryId)

    suspend fun updateSummaryHtmlPath(summaryId: String, summaryHtmlPath: String) {
        db.entrySummaryDao().updateHtmlPath(summaryId, summaryHtmlPath)
    }

    fun observeSummaryCount(entryId: String): Flow<Int> =
        db.entrySummaryDao().observeCountByEntry(entryId)

    fun observeSummary(summaryId: String): Flow<EntrySummaryEntity?> =
        db.entrySummaryDao().observeById(summaryId)

    suspend fun createEntry(title: String?): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.entryDao().upsert(
            EntryEntity(
                id = id,
                title = title,
                shortTitle = null,
                createdAtEpochMs = now,
                status = "DRAFT",
                lastError = null,
                processingStage = null,
                progressCurrent = null,
                progressTotal = null,
                progressMessage = null,
                updatedAtEpochMs = now,
                slidesPdfPath = null,
                speakerName = null,
                speakerAffiliation = null,
                talkTitle = null,
                keywords = null,
                finalSummary = null,
                summaryPdfPath = null,
                summaryMdPath = null,
                summaryHtmlPath = null,
            ),
        )
        ensureEntryDir(id)
        return id
    }

    suspend fun createEntryAutoTitle(prefix: String = "新建条目"): String {
        var n = 1
        while (true) {
            val title = if (n == 1) prefix else "$prefix$n"
            if (db.entryDao().countByTitle(title) == 0) {
                return createEntry(title = title)
            }
            n += 1
        }
    }

    suspend fun updateEntryStatus(entryId: String, status: String, lastError: String? = null) {
        db.entryDao().updateStatus(entryId, status, lastError)
    }

    suspend fun updateEntryProgress(
        entryId: String,
        status: String,
        stage: String?,
        current: Int?,
        total: Int?,
        message: String?,
        lastError: String? = null,
    ) {
        db.entryDao().updateProgress(
            id = entryId,
            status = status,
            lastError = lastError,
            stage = stage,
            current = current,
            total = total,
            message = message,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    suspend fun cancelAllRunningEntries(message: String) {
        db.entryDao().cancelAllRunning(message = message, updatedAtEpochMs = System.currentTimeMillis())
    }

    suspend fun updateImageDisplay(imageId: String, displayOrder: Int?, displayName: String?) {
        db.entryImageDao().updateDisplay(imageId, displayOrder, displayName)
    }

    suspend fun updateImageDisplayOrder(imageId: String, displayOrder: Int?) {
        db.entryImageDao().updateDisplayOrder(imageId, displayOrder)
    }

    suspend fun upsertEntryImages(items: List<EntryImageEntity>) {
        db.entryImageDao().upsertAll(items)
    }

    suspend fun getEntryImages(entryId: String): List<EntryImageEntity> = db.entryImageDao().getByEntry(entryId)

    suspend fun deleteEntryImage(entryId: String, imageId: String) {
        val images = db.entryImageDao().getByEntry(entryId)
        val target = images.firstOrNull { it.id == imageId } ?: return
        runCatching { File(target.localPath).delete() }
        db.slideAnalysisDao().deleteByImageId(imageId)
        db.entryImageDao().deleteById(imageId)
        val remaining = db.entryImageDao().getByEntry(entryId).sortedBy { it.createdAtEpochMs }
        for ((idx, img) in remaining.withIndex()) {
            val order = idx + 1
            updateImageDisplayOrder(img.id, order)
        }
    }

    suspend fun deleteEntryPdf(entryId: String, pdfId: String) {
        val pdfs = db.entryPdfDao().getByEntry(entryId).sortedBy { it.displayOrder }
        val target = pdfs.firstOrNull { it.id == pdfId } ?: return
        runCatching { File(target.localPath).delete() }
        db.entryPdfDao().deleteById(pdfId)
        val remaining = db.entryPdfDao().getByEntry(entryId).sortedBy { it.displayOrder }
        for ((idx, pdf) in remaining.withIndex()) {
            val order = idx + 1
            val desired = File(entryDir(entryId), "slides_$order.pdf")
            val current = File(pdf.localPath)
            val newPath =
                if (current.absolutePath != desired.absolutePath) {
                    val ok = runCatching { current.renameTo(desired) }.getOrDefault(false)
                    if (!ok) {
                        runCatching {
                            current.inputStream().use { input ->
                                desired.outputStream().use { output -> input.copyTo(output) }
                            }
                            current.delete()
                        }
                    }
                    desired.absolutePath
                } else {
                    pdf.localPath
                }
            db.entryPdfDao().updateOrderAndPath(pdf.id, order, newPath)
        }
        val entry = db.entryDao().getById(entryId) ?: return
        val firstPdf = db.entryPdfDao().getByEntry(entryId).minByOrNull { it.displayOrder }?.localPath
        db.entryDao().upsert(entry.copy(slidesPdfPath = firstPdf, updatedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun deleteEntry(entryId: String) {
        runCatching {
            db.slideAnalysisDao().deleteByEntry(entryId)
            db.entryImageDao().deleteByEntry(entryId)
            db.entryPdfDao().deleteByEntry(entryId)
            db.entrySummaryDao().deleteByEntry(entryId)
            db.entryDao().deleteById(entryId)
        }
        runCatching {
            val dir = entryDir(entryId)
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    suspend fun saveSlideAnalyses(items: List<SlideAnalysisEntity>) {
        db.slideAnalysisDao().upsertAll(items)
    }

    suspend fun clearSlideAnalyses(entryId: String) {
        db.slideAnalysisDao().deleteByEntry(entryId)
    }

    suspend fun setFinalResult(
        entryId: String,
        shortTitle: String?,
        speakerName: String?,
        speakerAffiliation: String?,
        talkTitle: String?,
        keywords: String?,
        finalSummary: String,
        summaryMdPath: String?,
        summaryHtmlPath: String?,
    ) {
        val entry = requireNotNull(db.entryDao().getById(entryId))
        val now = System.currentTimeMillis()
        val summaryId = UUID.randomUUID().toString()
        val renamedTitle =
            normalizeEntryTitle(
                shortTitle?.takeIf { it.isNotBlank() }
                    ?: talkTitle?.takeIf { it.isNotBlank() }
                    ?: entry.title?.takeIf { it.isNotBlank() }
                    ?: "",
            ).ifBlank { entry.title ?: "未命名条目" }
        db.entrySummaryDao().upsert(
            EntrySummaryEntity(
                id = summaryId,
                entryId = entryId,
                createdAtEpochMs = now,
                shortTitle = renamedTitle,
                talkTitle = talkTitle,
                speakerName = speakerName,
                speakerAffiliation = speakerAffiliation,
                keywords = keywords,
                finalSummary = finalSummary,
                summaryPdfPath = null,
                summaryMdPath = summaryMdPath,
                summaryHtmlPath = summaryHtmlPath,
            ),
        )
        db.entryDao().upsert(
            entry.copy(
                title = renamedTitle,
                shortTitle = renamedTitle,
                speakerName = speakerName,
                speakerAffiliation = speakerAffiliation,
                talkTitle = talkTitle,
                keywords = keywords,
                finalSummary = finalSummary,
                summaryPdfPath = null,
                summaryMdPath = summaryMdPath,
                summaryHtmlPath = summaryHtmlPath,
                status = "SUCCEEDED",
                lastError = null,
                processingStage = null,
                progressCurrent = null,
                progressTotal = null,
                progressMessage = null,
                updatedAtEpochMs = now,
            ),
        )
    }

    private fun normalizeEntryTitle(raw: String): String {
        val cleaned = raw.trim().replace(Regex("""\s+"""), " ")
        if (cleaned.length <= MaxEntryTitleChars) return cleaned
        return cleaned.take((MaxEntryTitleChars - 1).coerceAtLeast(1)) + "…"
    }

    suspend fun importSlidesPdfFromUri(entryId: String, sourceUri: Uri): String {
        ensureEntryDir(entryId)
        val count = db.entryPdfDao().countByEntry(entryId)
        if (count >= MaxPdfsPerEntry) throw IllegalStateException(PdfLimitMessage)
        val order = count + 1
        val outFile = File(entryDir(entryId), "slides_$order.pdf")
        val displayName = queryDisplayName(sourceUri)?.takeIf { it.isNotBlank() }
        val input = requireNotNull(appContext.contentResolver.openInputStream(sourceUri))
        input.use { i ->
            outFile.outputStream().use { output -> i.copyTo(output) }
        }
        db.entryPdfDao().upsert(
            EntryPdfEntity(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                createdAtEpochMs = System.currentTimeMillis(),
                displayOrder = order,
                displayName = displayName,
                localPath = outFile.absolutePath,
            ),
        )
        val entry = requireNotNull(db.entryDao().getById(entryId))
        val legacy = if (entry.slidesPdfPath.isNullOrBlank()) outFile.absolutePath else entry.slidesPdfPath
        db.entryDao().upsert(entry.copy(slidesPdfPath = legacy, updatedAtEpochMs = System.currentTimeMillis()))
        return outFile.absolutePath
    }

    suspend fun importImageFromUri(entryId: String, sourceUri: Uri): String {
        ensureEntryDir(entryId)
        val imageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val outFile = File(entryImagesDir(entryId), "$imageId.jpg")
        val input = requireNotNull(appContext.contentResolver.openInputStream(sourceUri))
        input.use { i ->
            outFile.outputStream().use { output -> i.copyTo(output) }
        }
        db.entryImageDao().upsertAll(
            listOf(
                EntryImageEntity(
                    id = imageId,
                    entryId = entryId,
                    localPath = outFile.absolutePath,
                    createdAtEpochMs = now,
                    pageIndex = null,
                    displayOrder = null,
                    displayName = null,
                ),
            ),
        )
        return imageId
    }

    suspend fun importImageFromFile(entryId: String, sourceFile: File): String {
        ensureEntryDir(entryId)
        val imageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val outFile = File(entryImagesDir(entryId), "$imageId.jpg")
        FileInputStream(sourceFile).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        db.entryImageDao().upsertAll(
            listOf(
                EntryImageEntity(
                    id = imageId,
                    entryId = entryId,
                    localPath = outFile.absolutePath,
                    createdAtEpochMs = now,
                    pageIndex = null,
                    displayOrder = null,
                    displayName = null,
                ),
            ),
        )
        return imageId
    }

    fun entryDir(entryId: String): File = File(appContext.filesDir, "entries/$entryId")

    fun entryImagesDir(entryId: String): File = File(entryDir(entryId), "images")

    fun entryPdfPagesDir(entryId: String): File = File(entryDir(entryId), "pdf_pages")

    suspend fun getContentType(uri: Uri): String? = appContext.contentResolver.getType(uri)

    suspend fun importSharedUris(entryId: String, uris: List<Uri>): Pair<Int, Boolean> {
        val pdfUris =
            uris.filter { (getContentType(it).orEmpty() == "application/pdf") || getContentType(it).orEmpty().endsWith("/pdf") }
        val existingPdfCount = db.entryPdfDao().countByEntry(entryId)
        if (existingPdfCount + pdfUris.size > MaxPdfsPerEntry) throw IllegalStateException(PdfLimitMessage)

        var imageCount = 0
        var pdfCountAdded = 0
        for (uri in uris) {
            val type = getContentType(uri).orEmpty()
            if (type.startsWith("image/")) {
                importImageFromUri(entryId, uri)
                imageCount += 1
            } else if (type == "application/pdf" || type.endsWith("/pdf")) {
                importSlidesPdfFromUri(entryId, uri)
                pdfCountAdded += 1
            }
        }
        return imageCount to (pdfCountAdded > 0)
    }

    private fun ensureEntryDir(entryId: String) {
        entryImagesDir(entryId).mkdirs()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cr = appContext.contentResolver
        val cursor = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) return null
            return c.getString(idx)
        }
    }
}
