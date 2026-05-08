package com.lzt.summaryofslides.ui.entrylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: EntryListViewModel = viewModel()
    val entries = vm.entries.collectAsState()
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEntry(entry.id) },
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
