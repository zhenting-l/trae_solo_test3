package com.lzt.summaryofslides.ui.summary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntrySummaryEntity
import com.lzt.summaryofslides.util.ShareUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryHistoryViewModel(private val entryId: String) : ViewModel() {
    private val repo = AppContainer.entryRepository

    val summaries: StateFlow<List<EntrySummaryEntity>> =
        repo.observeSummaries(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class SummaryHistoryViewModelFactory(private val entryId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SummaryHistoryViewModel(entryId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryHistoryScreen(
    entryId: String,
    onBack: () -> Unit,
    onOpenEntrySummary: (String) -> Unit,
) {
    val vm: SummaryHistoryViewModel = viewModel(factory = SummaryHistoryViewModelFactory(entryId))
    val summariesState = vm.summaries.collectAsState()
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("总结历史") },
                navigationIcon = { Button(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(summariesState.value, key = { _, s -> s.id }) { index, s ->
                val bg =
                    if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(s.shortTitle ?: s.talkTitle ?: "总结")
                        Text(sdf.format(Date(s.createdAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { onOpenEntrySummary(s.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("查看")
                            }
                            val mdPath = s.summaryMdPath
                            Button(
                                onClick = {
                                    if (!mdPath.isNullOrBlank()) {
                                        ShareUtil.shareSingleFile(context, File(mdPath), "text/markdown")
                                    }
                                },
                                enabled = !mdPath.isNullOrBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("导出MD")
                            }
                        }
                    }
                }
            }
        }
    }
}
