package com.lzt.summaryofslides.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("UPDATE entries SET status = :status, lastError = :lastError WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, lastError: String?)

    @Query(
        """
        UPDATE entries
        SET
            status = :status,
            lastError = :lastError,
            processingStage = :stage,
            progressCurrent = :current,
            progressTotal = :total,
            progressMessage = :message,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        lastError: String?,
        stage: String?,
        current: Int?,
        total: Int?,
        message: String?,
        updatedAtEpochMs: Long,
    )

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE title = :title")
    suspend fun countByTitle(title: String): Int

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM entries ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<EntryEntity?>

    @Query(
        """
        UPDATE entries
        SET
            status = 'CANCELLED',
            lastError = NULL,
            processingStage = 'CANCELLED',
            progressCurrent = NULL,
            progressTotal = NULL,
            progressMessage = :message,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE status IN ('QUEUED','PROCESSING')
        """,
    )
    suspend fun cancelAllRunning(message: String, updatedAtEpochMs: Long)
}

@Dao
interface EntryImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EntryImageEntity>)

    @Query("UPDATE entry_images SET displayOrder = :displayOrder, displayName = :displayName WHERE id = :id")
    suspend fun updateDisplay(id: String, displayOrder: Int?, displayName: String?)

    @Query("UPDATE entry_images SET displayOrder = :displayOrder WHERE id = :id")
    suspend fun updateDisplayOrder(id: String, displayOrder: Int?)

    @Query("UPDATE entry_images SET localPath = :localPath WHERE id = :id")
    suspend fun updateLocalPath(id: String, localPath: String)

    @Query("DELETE FROM entry_images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM entry_images WHERE entryId = :entryId")
    suspend fun deleteByEntry(entryId: String)

    @Query("SELECT * FROM entry_images WHERE entryId = :entryId ORDER BY createdAtEpochMs ASC")
    suspend fun getByEntry(entryId: String): List<EntryImageEntity>

    @Query(
        """
        SELECT * FROM entry_images
        WHERE entryId = :entryId
        ORDER BY
            CASE WHEN displayOrder IS NULL THEN 1 ELSE 0 END,
            displayOrder ASC,
            createdAtEpochMs ASC
        """,
    )
    fun observeByEntry(entryId: String): Flow<List<EntryImageEntity>>
}

@Dao
interface SlideAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SlideAnalysisEntity>)

    @Query("SELECT * FROM slide_analyses WHERE entryId = :entryId ORDER BY createdAtEpochMs ASC")
    suspend fun getByEntry(entryId: String): List<SlideAnalysisEntity>

    @Query("SELECT * FROM slide_analyses WHERE entryId = :entryId ORDER BY createdAtEpochMs ASC")
    fun observeByEntry(entryId: String): Flow<List<SlideAnalysisEntity>>

    @Query("DELETE FROM slide_analyses WHERE entryId = :entryId")
    suspend fun deleteByEntry(entryId: String)

    @Query("DELETE FROM slide_analyses WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: String)
}

@Dao
interface EntrySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EntrySummaryEntity)

    @Query("SELECT * FROM entry_summaries WHERE entryId = :entryId ORDER BY createdAtEpochMs DESC")
    fun observeByEntry(entryId: String): Flow<List<EntrySummaryEntity>>

    @Query("SELECT COUNT(*) FROM entry_summaries WHERE entryId = :entryId")
    fun observeCountByEntry(entryId: String): Flow<Int>

    @Query("SELECT * FROM entry_summaries WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<EntrySummaryEntity?>

    @Query("SELECT * FROM entry_summaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EntrySummaryEntity?

    @Query("SELECT COUNT(*) FROM entry_summaries WHERE entryId = :entryId")
    suspend fun countByEntry(entryId: String): Int

    @Query("UPDATE entry_summaries SET summaryHtmlPath = :summaryHtmlPath WHERE id = :id")
    suspend fun updateHtmlPath(id: String, summaryHtmlPath: String)

    @Query("DELETE FROM entry_summaries WHERE entryId = :entryId")
    suspend fun deleteByEntry(entryId: String)
}

@Dao
interface EntryPdfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EntryPdfEntity)

    @Query("SELECT * FROM entry_pdfs WHERE entryId = :entryId ORDER BY displayOrder ASC")
    fun observeByEntry(entryId: String): Flow<List<EntryPdfEntity>>

    @Query("SELECT * FROM entry_pdfs WHERE entryId = :entryId ORDER BY displayOrder ASC")
    suspend fun getByEntry(entryId: String): List<EntryPdfEntity>

    @Query("SELECT COUNT(*) FROM entry_pdfs WHERE entryId = :entryId")
    suspend fun countByEntry(entryId: String): Int

    @Query("DELETE FROM entry_pdfs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM entry_pdfs WHERE entryId = :entryId")
    suspend fun deleteByEntry(entryId: String)

    @Query("UPDATE entry_pdfs SET displayOrder = :displayOrder, localPath = :localPath WHERE id = :id")
    suspend fun updateOrderAndPath(id: String, displayOrder: Int, localPath: String)
}
