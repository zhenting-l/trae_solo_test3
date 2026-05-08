package com.lzt.summaryofslides.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val shortTitle: String?,
    val createdAtEpochMs: Long,
    val status: String,
    val lastError: String?,
    val processingStage: String?,
    val progressCurrent: Int?,
    val progressTotal: Int?,
    val progressMessage: String?,
    val updatedAtEpochMs: Long,
    val slidesPdfPath: String?,
    val speakerName: String?,
    val speakerAffiliation: String?,
    val talkTitle: String?,
    val keywords: String?,
    val finalSummary: String?,
    val summaryPdfPath: String?,
    val summaryMdPath: String?,
    val summaryHtmlPath: String?,
)

@Entity(
    tableName = "entry_images",
    indices = [Index(value = ["entryId"])],
)
data class EntryImageEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val localPath: String,
    val createdAtEpochMs: Long,
    val pageIndex: Int?,
    val displayOrder: Int?,
    val displayName: String?,
)

@Entity(
    tableName = "slide_analyses",
    indices = [Index(value = ["entryId"]), Index(value = ["imageId"])],
)
data class SlideAnalysisEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val imageId: String,
    val extractedJson: String?,
    val extractedText: String?,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "entry_summaries",
    indices = [Index(value = ["entryId"])],
)
data class EntrySummaryEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val createdAtEpochMs: Long,
    val shortTitle: String?,
    val talkTitle: String?,
    val speakerName: String?,
    val speakerAffiliation: String?,
    val keywords: String?,
    val finalSummary: String,
    val summaryPdfPath: String?,
    val summaryMdPath: String?,
    val summaryHtmlPath: String?,
)

@Entity(
    tableName = "entry_pdfs",
    indices = [Index(value = ["entryId"])],
)
data class EntryPdfEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val createdAtEpochMs: Long,
    val displayOrder: Int,
    val displayName: String?,
    val localPath: String,
)
