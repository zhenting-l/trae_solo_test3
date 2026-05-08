package com.lzt.summaryofslides.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    entryId: String,
    onBack: () -> Unit,
) {
    val vm: ShareViewModel = viewModel(factory = ShareViewModelFactory(entryId))
    val imagesState = vm.images.collectAsState()
    val pdfsState = vm.pdfs.collectAsState()
    val summariesState = vm.summaries.collectAsState()
    val context = LocalContext.current

    var selectedSummaryMdIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedSummaryHtmlIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUploadKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分享") },
                navigationIcon = { Button(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LaunchedEffect(summariesState.value) {
                if (summariesState.value.isNotEmpty()) {
                    vm.ensureSummaryHtmlFiles()
                }
                if ((selectedSummaryMdIds.isEmpty() && selectedSummaryHtmlIds.isEmpty()) && summariesState.value.isNotEmpty()) {
                    val latest = summariesState.value.first()
                    if (!latest.summaryMdPath.isNullOrBlank()) selectedSummaryMdIds = setOf(latest.id)
                    if (!latest.summaryHtmlPath.isNullOrBlank()) selectedSummaryHtmlIds = setOf(latest.id)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (summariesState.value.isNotEmpty()) {
                    item { Text("MD总结文档") }
                    items(summariesState.value, key = { it.id + "-md" }) { s ->
                        val path = s.summaryMdPath
                        if (!path.isNullOrBlank()) {
                            val checked = selectedSummaryMdIds.contains(s.id)
                            val fileName = File(path).name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { c ->
                                        selectedSummaryMdIds =
                                            if (c) selectedSummaryMdIds + s.id else selectedSummaryMdIds - s.id
                                    },
                                )
                                Text(fileName)
                            }
                        }
                    }

                    item { Text("HTML文档") }
                    items(summariesState.value, key = { it.id + "-html" }) { s ->
                        val path = s.summaryHtmlPath
                        if (!path.isNullOrBlank()) {
                            val checked = selectedSummaryHtmlIds.contains(s.id)
                            val fileName = File(path).name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { c ->
                                        selectedSummaryHtmlIds =
                                            if (c) selectedSummaryHtmlIds + s.id else selectedSummaryHtmlIds - s.id
                                    },
                                )
                                Text(fileName)
                            }
                        }
                    }
                }

                item { Text("用户上传的文件") }
                items(pdfsState.value, key = { it.id }) { pdf ->
                    val key = "pdf:${pdf.id}"
                    val checked = selectedUploadKeys.contains(key)
                    val rawName = pdf.displayName?.ifBlank { null } ?: File(pdf.localPath).name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { c ->
                                selectedUploadKeys =
                                    if (c) selectedUploadKeys + key else selectedUploadKeys - key
                            },
                        )
                        Text(rawName)
                    }
                }
                items(imagesState.value, key = { it.id }) { img ->
                    val order = img.displayOrder ?: 0
                    val displayName = img.displayName?.ifBlank { null } ?: File(img.localPath).nameWithoutExtension
                    val key = "img:${img.id}"
                    val checked = selectedUploadKeys.contains(key)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { c ->
                                selectedUploadKeys =
                                    if (c) selectedUploadKeys + key else selectedUploadKeys - key
                            },
                        )
                        Text("${order}-${displayName}.jpg")
                    }
                }
            }

            Button(
                onClick = { vm.share(context, selectedSummaryMdIds, selectedSummaryHtmlIds, selectedUploadKeys) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("分享所选内容")
            }
        }
    }
}
