package com.example.academicreportassistant.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object WorkEnqueuer {
    private const val AnalysisTag = "analysis"

    fun cancelAnalyzeEntry(context: Context, entryId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("analyze-$entryId")
        WorkManager.getInstance(context).cancelAllWorkByTag("entry-$entryId")
    }

    fun cancelAllAnalyses(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(AnalysisTag)
    }

    fun enqueueAnalyzeEntry(
        context: Context,
        entryId: String,
        source: String? = null,
        pdfMode: String? = null,
    ) {
        val req =
            OneTimeWorkRequestBuilder<AnalyzeEntryWorker>()
                .setInputData(
                    workDataOf(
                        "entryId" to entryId,
                        "source" to source,
                        "pdfMode" to pdfMode,
                    ),
                )
                .addTag(AnalysisTag)
                .addTag("entry-$entryId")
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("analyze-$entryId", ExistingWorkPolicy.REPLACE, req)
    }
}
