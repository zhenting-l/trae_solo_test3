package com.lzt.summaryofslides.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import android.content.Context
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.work.workDataOf

object WorkEnqueuer {
    private const val AnalysisTag = "analysis"

    fun cancelAnalyzeEntry(context: Context, entryId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("entry-$entryId")
        WorkManager.getInstance(context).cancelUniqueWork("analysis-singleton")
    }

    fun cancelAllAnalyses(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(AnalysisTag)
        WorkManager.getInstance(context).cancelUniqueWork("analysis-singleton")
    }

    fun enqueueAnalyzeEntry(
        context: Context,
        entryId: String,
        source: String? = null,
        extraPrompt: String? = null,
        batchImages: Boolean = false,
        incremental: Boolean = false,
    ) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val req =
            OneTimeWorkRequestBuilder<AnalyzeEntryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        "entryId" to entryId,
                        "source" to source,
                        "extraPrompt" to extraPrompt,
                        "batchImages" to batchImages,
                        "incremental" to incremental,
                    ),
                )
                .addTag(AnalysisTag)
                .addTag("entry-$entryId")
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("analysis-singleton", ExistingWorkPolicy.KEEP, req)
    }
}
