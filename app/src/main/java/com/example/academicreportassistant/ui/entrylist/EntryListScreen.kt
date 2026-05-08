package com.lzt.summaryofslides.ui.entrylist

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntryListScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: EntryListViewModel = viewModel()
    val entries = vm.entries.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var pendingDeleteEntryId by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("幻灯片条目") },
                actions = { Button(onClick = onOpenSettings) { Text("设置") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.createEntry(onOpenEntry) }) {
                Text("新建")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(entries.value, key = { _, e -> e.id }) { index, entry ->
                val bg =
                    if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                val statusColor =
                    when (entry.status) {
                        "SUCCEEDED" -> MaterialTheme.colorScheme.primary
                        "FAILED" -> MaterialTheme.colorScheme.error
                        "PROCESSING" -> MaterialTheme.colorScheme.tertiary
                        "QUEUED" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outline
                    }

                val maxRevealPx = with(density) { 84.dp.toPx() }
                val offsetX = remember(entry.id) { Animatable(0f) }
                val reveal = (-offsetX.value / maxRevealPx).coerceIn(0f, 1f)

                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Button(
                            onClick = { pendingDeleteEntryId = entry.id },
                            modifier =
                                Modifier
                                    .padding(end = 12.dp)
                                    .graphicsLayer { alpha = reveal },
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White,
                                ),
                            enabled = reveal > 0.05f,
                        ) {
                            Text("删除")
                        }
                    }

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = offsetX.value }
                                .pointerInput(entry.id) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dragAmount ->
                                            val next = (offsetX.value + dragAmount).coerceIn(-maxRevealPx, 0f)
                                            scope.launch { offsetX.snapTo(next) }
                                        },
                                        onDragEnd = {
                                            scope.launch {
                                                val target = if (offsetX.value <= -maxRevealPx * 0.5f) -maxRevealPx else 0f
                                                offsetX.animateTo(target)
                                            }
                                        },
                                    )
                                }
                                .combinedClickable(
                                    onClick = {
                                        if (offsetX.value != 0f) {
                                            scope.launch { offsetX.animateTo(0f) }
                                        } else {
                                            onOpenEntry(entry.id)
                                        }
                                    },
                                    onLongClick = { pendingDeleteEntryId = entry.id },
                                ),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = BorderStroke(1.dp, statusColor),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = entry.shortTitle ?: entry.title ?: entry.talkTitle ?: "未命名条目",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 3,
                                )
                                val sub = entry.talkTitle ?: entry.title
                                if (!sub.isNullOrBlank() && entry.shortTitle != null) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                            }
                            Text(
                                text = entry.status,
                                color = statusColor,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    val deleteId = pendingDeleteEntryId
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteEntryId = null },
            title = { Text("确认删除？") },
            text = { Text("删除该条目及其所有文件（图片/PDF/总结）？") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteEntryId = null
                        vm.deleteEntry(context, deleteId)
                    },
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                        ),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                Button(onClick = { pendingDeleteEntryId = null }) {
                    Text("取消")
                }
            },
        )
    }
}
