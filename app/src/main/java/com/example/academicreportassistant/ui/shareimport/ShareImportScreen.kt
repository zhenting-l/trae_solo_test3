package com.lzt.summaryofslides.ui.shareimport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryEntity
import com.lzt.summaryofslides.data.repo.EntryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShareImportViewModel : ViewModel() {
    private val repo = AppContainer.entryRepository

    val entries: StateFlow<List<EntryEntity>> =
        repo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun importToNewEntry(payload: SharePayload, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val entryId = repo.createEntryAutoTitle()
            runCatching { repo.importSharedUris(entryId, payload.uris) }
                .onSuccess { onDone(entryId) }
                .onFailure { onError(it.message ?: EntryRepository.PdfLimitMessage) }
        }
    }

    fun importToEntry(payload: SharePayload, entryId: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.importSharedUris(entryId, payload.uris) }
                .onSuccess { onDone(entryId) }
                .onFailure { onError(it.message ?: EntryRepository.PdfLimitMessage) }
        }
    }
}

class ShareImportViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ShareImportViewModel() as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareImportScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val vm: ShareImportViewModel = viewModel(factory = ShareImportViewModelFactory())
    val entriesState = vm.entries.collectAsState()
    val payload = remember { SharePayloadHolder.consume() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fileCount = payload?.uris?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入分享内容") },
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
            Text("待导入文件：$fileCount")
            Button(
                onClick = {
                    if (payload != null) {
                        vm.importToNewEntry(payload, onOpenEntry) { msg -> errorMessage = msg }
                    }
                },
                enabled = payload != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新建条目并导入")
            }

            Text("导入到已有条目")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(entriesState.value, key = { _, e -> e.id }) { index, entry ->
                    val bg =
                        if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (payload != null) {
                                    vm.importToEntry(payload, entry.id, onOpenEntry) { msg -> errorMessage = msg }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(entry.shortTitle ?: entry.title ?: entry.talkTitle ?: "未命名条目")
                            Text(entry.status, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (!errorMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("提示") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("确定")
                }
            },
        )
    }
}
