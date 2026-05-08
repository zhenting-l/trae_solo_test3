package com.example.academicreportassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EntryEntity::class,
        EntryImageEntity::class,
        SlideAnalysisEntity::class,
        EntrySummaryEntity::class,
        EntryPdfEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun entryImageDao(): EntryImageDao
    abstract fun slideAnalysisDao(): SlideAnalysisDao
    abstract fun entrySummaryDao(): EntrySummaryDao
    abstract fun entryPdfDao(): EntryPdfDao
}
