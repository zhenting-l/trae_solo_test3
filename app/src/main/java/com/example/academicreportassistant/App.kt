package com.example.academicreportassistant

import android.app.Application
import com.example.academicreportassistant.data.AppContainer
import com.example.academicreportassistant.worker.WorkEnqueuer
import kotlinx.coroutines.runBlocking

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        runBlocking {
            AppContainer.entryRepository.cancelAllRunningEntries("已中止（应用退出）")
        }
        WorkEnqueuer.cancelAllAnalyses(this)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                WorkEnqueuer.cancelAllAnalyses(this)
                runBlocking {
                    AppContainer.entryRepository.cancelAllRunningEntries("已中止（应用异常退出）")
                }
            }
            previous?.uncaughtException(t, e)
        }
    }
}
