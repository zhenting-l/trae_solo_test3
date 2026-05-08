package com.example.academicreportassistant.ui.summary

import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.academicreportassistant.data.AppContainer
import com.example.academicreportassistant.data.db.EntrySummaryEntity
import com.example.academicreportassistant.util.MarkdownHtmlUtil
import com.example.academicreportassistant.util.MarkdownRender
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SummaryDetailViewModel(private val summaryId: String) : ViewModel() {
    private val repo = AppContainer.entryRepository

    val summary: StateFlow<EntrySummaryEntity?> =
        repo.observeSummary(summaryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class SummaryDetailViewModelFactory(private val summaryId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SummaryDetailViewModel(summaryId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDetailScreen(
    summaryId: String,
    onBack: () -> Unit,
) {
    val vm: SummaryDetailViewModel = viewModel(factory = SummaryDetailViewModelFactory(summaryId))
    val summaryState = vm.summary.collectAsState()
    val markdown = summaryState.value?.finalSummary.orEmpty()
    var useWebView by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("总结（历史）") },
                navigationIcon = { Button(onClick = onBack) { Text("返回") } },
                actions = {
                    Button(onClick = { useWebView = !useWebView }) {
                        Text(if (useWebView) "原生" else "网页")
                    }
                },
            )
        },
    ) { padding ->
        if (useWebView) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                    }
                },
                update = { webView ->
                    val bodyHtml = MarkdownHtmlUtil.toHtml(markdown)
                    webView.loadDataWithBaseURL(
                        "https://app.local/",
                        buildHtml(bodyHtml),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                factory = { context ->
                    TextView(context).apply {
                        textSize = 16f
                    }
                },
                update = { tv ->
                    MarkdownRender.get(tv.context).setMarkdown(tv, markdown)
                },
            )
        }
    }
}

private fun buildHtml(bodyHtml: String): String {
    return """
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, "Noto Sans", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif; padding: 16px; line-height: 1.6; }
      pre { overflow-x: auto; background: #f6f8fa; padding: 12px; border-radius: 8px; }
      code { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; }
      blockquote { border-left: 4px solid #ddd; padding-left: 12px; color: #555; }
      table { border-collapse: collapse; width: 100%; }
      th, td { border: 1px solid #ddd; padding: 6px; }
    </style>
  </head>
  <body>
    <div id="content">$bodyHtml</div>
  </body>
</html>
""".trimIndent()
}

