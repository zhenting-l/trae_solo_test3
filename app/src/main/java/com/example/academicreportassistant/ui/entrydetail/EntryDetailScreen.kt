package com.lzt.summaryofslides.ui.entrydetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.lzt.summaryofslides.data.repo.EntryRepository
import com.lzt.summaryofslides.util.createTempCameraFile
import com.lzt.summaryofslides.util.fileToContentUri
import com.lzt.summaryofslides.util.openPdf
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenSummaryHistory: () -> Unit,
) {
    val vm: EntryDetailViewModel = viewModel(factory = EntryDetailViewModelFactory(entryId))
    val entryState = vm.entry.collectAsState()
    val imagesState = vm.images.collectAsState()
    val pdfsState = vm.pdfs.collectAsState()
    val errorState = vm.errorMessage.collectAsState()
    val summaryCountState = vm.summaryCount.collectAsState()
    val context = LocalContext.current
    var pendingCameraFile: File? by remember { mutableStateOf(null) }
    var showPdfListDialog by remember { mutableStateOf(false) }
    var pendingDeleteImageId by remember { mutableStateOf<String?>(null) }
    var pendingDeletePdfId by remember { mutableStateOf<String?>(null) }
    var previewImageFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingCameraFile
            if (success && file != null) {
                vm.importCapturedFile(file)
            } else {
                file?.delete()
            }
            pendingCameraFile = null
        }

    val pickImagesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            vm.importGalleryUris(uris)
        }
    val pickPdfLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.importSlidesPdf(uri)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryState.value?.title ?: entryState.value?.talkTitle ?: "条目详情") },
                navigationIcon = { Button(onClick = onBack) { Text("返回") } },
                actions = { Button(onClick = onShare) { Text("分享") } },
            )
        },
    ) { padding ->
        LaunchedEffect(showPdfListDialog, pdfsState.value.size) {
            if (showPdfListDialog && pdfsState.value.isEmpty()) {
                showPdfListDialog = false
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val importBtnColors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val file = createTempCameraFile(context)
                        pendingCameraFile = file
                        takePictureLauncher.launch(fileToContentUri(context, file))
                    },
                    modifier = Modifier.weight(1f),
                    colors = importBtnColors,
                ) {
                    Text("拍照")
                }
                Button(
                    onClick = { pickImagesLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = importBtnColors,
                ) {
                    Text("相册导入")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { pickPdfLauncher.launch("application/pdf") },
                    enabled = pdfsState.value.size < EntryRepository.MaxPdfsPerEntry,
                    modifier = Modifier.weight(1f),
                    colors = importBtnColors,
                ) {
                    Text("上传PDF")
                }
                Button(
                    onClick = { showPdfListDialog = true },
                    enabled = pdfsState.value.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = importBtnColors,
                ) {
                    Text("查看PDF")
                }
            }

            if (pdfsState.value.size >= EntryRepository.MaxPdfsPerEntry) {
                Text(
                    EntryRepository.PdfLimitMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    val entry = entryState.value
                    val status = entry?.status ?: "-"
                    val hasImages = imagesState.value.isNotEmpty()
                    val hasPdfs = pdfsState.value.isNotEmpty()
                    if (status == "QUEUED" || status == "PROCESSING") {
                        vm.cancelAnalysis(context)
                    } else {
                        vm.startAnalysis(context)
                    }
                },
                enabled =
                    (imagesState.value.isNotEmpty() || pdfsState.value.isNotEmpty()) ||
                        (entryState.value?.status == "QUEUED" || entryState.value?.status == "PROCESSING"),
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        disabledContentColor = Color.White,
                    ),
            ) {
                val status = entryState.value?.status ?: "-"
                val text =
                    when (status) {
                        "QUEUED", "PROCESSING" -> "中断任务"
                        "FAILED", "CANCELLED" -> "重新开始分析总结"
                        else -> "开始分析并生成总结"
                    }
                Text(text)
            }

            if (summaryCountState.value >= 2) {
                Button(
                    onClick = onOpenSummaryHistory,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text("查看总结历史（${summaryCountState.value}）")
                }
            }

            if (showPdfListDialog) {
                AlertDialog(
                    onDismissRequest = { showPdfListDialog = false },
                    title = { Text("查看PDF") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (pdf in pdfsState.value) {
                                val rawName = pdf.displayName?.ifBlank { null } ?: File(pdf.localPath).name
                                val base = rawName.removeSuffix(".pdf")
                                val shownBase = if (base.length > 10) base.take(10) + "..." else base
                                val shownName = "$shownBase.pdf"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = {
                                            showPdfListDialog = false
                                            openPdf(context, File(pdf.localPath))
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(shownName)
                                    }
                                    Button(
                                        onClick = { pendingDeletePdfId = pdf.id },
                                        modifier = Modifier.fillMaxHeight(),
                                    ) {
                                        Text("X")
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            }

            if (pendingDeletePdfId != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeletePdfId = null },
                    title = { Text("确认删除？") },
                    text = { Text("是否删除该PDF？") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val id = pendingDeletePdfId
                                pendingDeletePdfId = null
                                if (id != null) vm.deletePdf(id)
                            },
                        ) {
                            Text("是")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { pendingDeletePdfId = null }) {
                            Text("否")
                        }
                    },
                )
            }

            if (pendingDeleteImageId != null) {
                AlertDialog(
                    onDismissRequest = { pendingDeleteImageId = null },
                    title = { Text("确认删除？") },
                    text = { Text("是否删除该图片？") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val id = pendingDeleteImageId
                                pendingDeleteImageId = null
                                if (id != null) vm.deleteImage(id)
                            },
                        ) {
                            Text("是")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { pendingDeleteImageId = null }) {
                            Text("否")
                        }
                    },
                )
            }

            if (!errorState.value.isNullOrBlank()) {
                AlertDialog(
                    onDismissRequest = { vm.clearError() },
                    title = { Text("提示") },
                    text = { Text(errorState.value.orEmpty()) },
                    confirmButton = {
                        Button(onClick = { vm.clearError() }) {
                            Text("确定")
                        }
                    },
                )
            }

            val entry = entryState.value
            val status = entry?.status ?: "-"
            val hasSlidesPdf = pdfsState.value.isNotEmpty()
            val pdfText = if (hasSlidesPdf) "，已上传PDF" else ""
            Text("已添加 ${imagesState.value.size} 张图片$pdfText；状态：$status")

            val stageText = entry?.progressMessage ?: entry?.processingStage
            val current = entry?.progressCurrent
            val total = entry?.progressTotal
            if (status == "QUEUED" || status == "PROCESSING") {
                if (!stageText.isNullOrBlank()) {
                    val suffix = if (current != null && total != null && total > 0) "（$current/$total）" else ""
                    Text("进度：$stageText$suffix")
                }
                if (current != null && total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            val lastError = entry?.lastError
            if (!lastError.isNullOrBlank()) Text("错误：$lastError")

            if (status == "SUCCEEDED") {
                Button(
                    onClick = onOpenSummary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看总结（Markdown）")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(imagesState.value, key = { it.id }) { img ->
                    var menuExpanded by remember(img.id) { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = File(img.localPath),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { previewImageFile = File(img.localPath) },
                            )
                            Button(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Text("...")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        menuExpanded = false
                                        pendingDeleteImageId = img.id
                                    },
                                )
                            }
                        }
                        val order = img.displayOrder
                        val name = img.displayName
                        if (order != null && !name.isNullOrBlank()) {
                            Text("${order}-${name}.jpg")
                        }
                    }
                }
            }
        }
    }

    val file = previewImageFile
    if (file != null) {
        Dialog(onDismissRequest = { previewImageFile = null }) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val state =
                rememberTransformableState { zoomChange, offsetChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 6f)
                    offset += offsetChange
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .transformable(state)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { previewImageFile = null })
                        },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                )
            }
        }
    }
}
