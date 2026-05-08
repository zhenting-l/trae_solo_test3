package com.lzt.summaryofslides

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.ui.AppNav
import com.lzt.summaryofslides.ui.shareimport.SharePayload
import com.lzt.summaryofslides.ui.shareimport.SharePayloadHolder
import com.lzt.summaryofslides.worker.WorkEnqueuer
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        setContent {
            MaterialTheme {
                Surface {
                    AppNav()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) {
            runCatching {
                WorkEnqueuer.cancelAllAnalyses(this)
                runBlocking {
                    AppContainer.entryRepository.cancelAllRunningEntries("已中止（应用退出）")
                }
            }
        }
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) uris += uri
            val cd = intent.clipData
            if (cd != null) {
                for (i in 0 until cd.itemCount) {
                    val u = cd.getItemAt(i).uri
                    if (u != null) uris += u
                }
            }
        } else {
            val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (list != null) uris += list
            val cd = intent.clipData
            if (cd != null) {
                for (i in 0 until cd.itemCount) {
                    val u = cd.getItemAt(i).uri
                    if (u != null) uris += u
                }
            }
        }
        val distinct = uris.distinct()
        if (distinct.isNotEmpty()) {
            SharePayloadHolder.set(SharePayload(distinct))
        }
    }
}
