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
    val entryState = vm.entry.collectAsState()
    val imagesState = vm.images.collectAsState()
    val pdfsState = vm.pdfs.collectAsState()
    val context = LocalContext.current

    var selectedImageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var includeSummaryMd by remember { mutableStateOf(true) }
    var includeSlidesPdf by remember { mutableStateOf(true) }

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
            val summaryPath = entryState.value?.summaryMdPath
            if (!summaryPath.isNullOrBlank() && File(summaryPath).exists()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeSummaryMd, onCheckedChange = { includeSummaryMd = it })
                    Text("包含总结Markdown")
                }
            }
            if (pdfsState.value.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeSlidesPdf, onCheckedChange = { includeSlidesPdf = it })
                    Text("包含幻灯片PDF（${pdfsState.value.size}）")
                }
            }

            Text("选择要分享的照片")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(imagesState.value, key = { it.id }) { img ->
                    val checked = selectedImageIds.contains(img.id)
                    val order = img.displayOrder ?: 0
                    val displayName = img.displayName?.ifBlank { null } ?: File(img.localPath).nameWithoutExtension
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { c ->
                                selectedImageIds =
                                    if (c) selectedImageIds + img.id else selectedImageIds - img.id
                            },
                        )
                        Text("${order}-${displayName}.jpg")
                    }
                }
            }

            Button(
                onClick = { vm.share(context, selectedImageIds, includeSummaryMd, includeSlidesPdf) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("分享所选内容")
            }
        }
    }
}
