package com.lzt.summaryofslides.ui.summary

import android.webkit.WebView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryEntity
import com.lzt.summaryofslides.util.MarkdownHtmlUtil
import com.lzt.summaryofslides.util.MarkdownHtmlTemplate
import com.lzt.summaryofslides.util.MarkdownRender
import com.lzt.summaryofslides.util.MarkdownTidyUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import android.widget.TextView
import androidx.compose.ui.unit.dp
import java.io.File

class SummaryViewModel(private val entryId: String) : ViewModel() {
    private val repo = AppContainer.entryRepository

    val entry: StateFlow<EntryEntity?> =
        repo.observeEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

class SummaryViewModelFactory(private val entryId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SummaryViewModel(entryId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    entryId: String,
    onBack: () -> Unit,
) {
    val vm: SummaryViewModel = viewModel(factory = SummaryViewModelFactory(entryId))
    val entryState = vm.entry.collectAsState()
    val markdown = MarkdownTidyUtil.tidy(normalizeStoredMarkdown(entryState.value?.finalSummary.orEmpty()))
    var useWebView by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("总结（Markdown）") },
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
                    val htmlPath = entryState.value?.summaryHtmlPath
                    if (!htmlPath.isNullOrBlank() && File(htmlPath).exists()) {
                        val html = runCatching { File(htmlPath).readText(Charsets.UTF_8) }.getOrNull()
                        if (!html.isNullOrBlank()) {
                            webView.loadDataWithBaseURL(
                                "https://app.local/",
                                html,
                                "text/html",
                                "utf-8",
                                null,
                            )
                        } else {
                            val bodyHtml = MarkdownHtmlUtil.toHtml(markdown)
                            webView.loadDataWithBaseURL(
                                "https://app.local/",
                                MarkdownHtmlTemplate.wrap(bodyHtml),
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    } else {
                        val bodyHtml = MarkdownHtmlUtil.toHtml(markdown)
                        webView.loadDataWithBaseURL(
                            "https://app.local/",
                            MarkdownHtmlTemplate.wrap(bodyHtml),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
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
                        setLineSpacing(0f, 1.35f)
                    }
                },
                update = { tv ->
                    MarkdownRender.get(tv.context).setMarkdown(tv, markdown)
                },
            )
        }
    }
}

private fun normalizeStoredMarkdown(raw: String): String {
    val text = raw.trim()
    val fenced =
        Regex("```(?:markdown|md)\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    if (!fenced.isNullOrBlank()) return fenced

    if (!text.startsWith("{")) return raw
    val idx = text.indexOf("final_summary")
    if (idx < 0) return raw
    val segment = text.substring(idx)
    val marker = Regex("(?m)^\\s*\"?#\\s+").find(segment)
    if (marker != null) {
        var md = segment.substring(marker.range.first).trim()
        if (md.startsWith("\"#")) md = md.drop(1)
        md = md.removeSuffix("\"")
        md = md.removeSuffix("}")
        md = md.removeSuffix("\"")
        return md.trim()
    }
    val hashIndex = segment.indexOf('#')
    if (hashIndex >= 0) {
        var md = segment.substring(hashIndex).trim()
        md = md.removeSuffix("\"")
        md = md.removeSuffix("}")
        md = md.removeSuffix("\"")
        return md.trim()
    }
    return raw
}

 
