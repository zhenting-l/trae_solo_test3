package com.lzt.summaryofslides.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lzt.summaryofslides.data.db.AppDatabase
import com.lzt.summaryofslides.data.repo.EntryRepository
import com.lzt.summaryofslides.settings.SettingsStore

object AppContainer {
    lateinit var appContext: Context
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var entryRepository: EntryRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set

    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN shortTitle TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN processingStage TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN progressCurrent INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN progressTotal INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN progressMessage TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE entry_images ADD COLUMN displayOrder INTEGER")
                db.execSQL("ALTER TABLE entry_images ADD COLUMN displayName TEXT")
            }
        }

    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN slidesPdfPath TEXT")
            }
        }

    private val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS entry_summaries (
                        id TEXT NOT NULL,
                        entryId TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        shortTitle TEXT,
                        talkTitle TEXT,
                        speakerName TEXT,
                        speakerAffiliation TEXT,
                        keywords TEXT,
                        finalSummary TEXT NOT NULL,
                        summaryPdfPath TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_summaries_entryId ON entry_summaries(entryId)")
            }
        }

    private val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS entry_pdfs (
                        id TEXT NOT NULL,
                        entryId TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        displayOrder INTEGER NOT NULL,
                        displayName TEXT,
                        localPath TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_pdfs_entryId ON entry_pdfs(entryId)")
                db.execSQL(
                    """
                    INSERT INTO entry_pdfs(id, entryId, createdAtEpochMs, displayOrder, displayName, localPath)
                    SELECT id || '-pdf1', id, updatedAtEpochMs, 1, NULL, slidesPdfPath
                    FROM entries
                    WHERE slidesPdfPath IS NOT NULL AND slidesPdfPath != ''
                    """.trimIndent(),
                )
            }
        }

    private val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasDisplayNameColumn =
                    db.query("PRAGMA table_info(entry_pdfs)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        var found = false
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == "displayName") {
                                found = true
                                break
                            }
                        }
                        found
                    }
                if (!hasDisplayNameColumn) {
                    db.execSQL("ALTER TABLE entry_pdfs ADD COLUMN displayName TEXT")
                }
            }
        }

    fun init(context: Context) {
        appContext = context.applicationContext
        database =
            Room.databaseBuilder(appContext, AppDatabase::class.java, "app.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        settingsStore = SettingsStore(appContext)
        entryRepository = EntryRepository(
            db = database,
            appContext = appContext,
        )
    }
}
