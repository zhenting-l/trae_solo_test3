package com.lzt.summaryofslides.ui.entrylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.data.db.EntryEntity
import com.lzt.summaryofslides.worker.WorkEnqueuer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EntryListViewModel : ViewModel() {
    private val repo = AppContainer.entryRepository

    val entries: StateFlow<List<EntryEntity>> =
        repo.observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createEntry(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repo.createEntryAutoTitle()
            onCreated(id)
        }
    }

    fun deleteEntry(context: Context, entryId: String) {
        viewModelScope.launch {
            WorkEnqueuer.cancelAnalyzeEntry(context, entryId)
            repo.deleteEntry(entryId)
        }
    }
}
