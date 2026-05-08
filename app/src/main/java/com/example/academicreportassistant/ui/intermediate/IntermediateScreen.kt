package com.example.academicreportassistant.ui.intermediate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.academicreportassistant.data.AppContainer
import com.example.academicreportassistant.data.db.EntryEntity
import com.example.academicreportassistant.data.db.EntryImageEntity
import com.example.academicreportassistant.data.db.SlideAnalysisEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private data class SlidePreview(
    val section: String?,
    val mainPoints: List<String>,
    val citations: List<String>,
)

class IntermediateViewModel(private val entryId: String) : ViewModel() {
    private val repo = AppContainer.entryRepository

    val entry: StateFlow<EntryEntity?> =
        repo.observeEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val images: StateFlow<List<EntryImageEntity>> =
        repo.observeEntryImages(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analyses: StateFlow<List<SlideAnalysisEntity>> =
        repo.observeSlideAnalyses(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class IntermediateViewModelFactory(private val entryId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return IntermediateViewModel(entryId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntermediateScreen(
    entryId: String,
    onBack: () -> Unit,
) {
    val vm: IntermediateViewModel = viewModel(factory = IntermediateViewModelFactory(entryId))
    val entryState = vm.entry.collectAsState()
    val imagesState = vm.images.collectAsState()
    val analysesState = vm.analyses.collectAsState()

    val entry = entryState.value
    val json = Json { ignoreUnknownKeys = true }

    val analysisByImageId = analysesState.value.associateBy { it.imageId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("中间输出（精选）") },
                navigationIcon = { Button(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(entry?.shortTitle ?: entry?.talkTitle ?: entry?.title ?: "未命名条目")
            if (!entry?.speakerName.isNullOrBlank() || !entry?.speakerAffiliation.isNullOrBlank()) {
                Text("报告人：${entry?.speakerName.orEmpty()} ${entry?.speakerAffiliation.orEmpty()}".trim())
            }
            if (!entry?.talkTitle.isNullOrBlank()) Text("标题：${entry?.talkTitle}")
            if (!entry?.keywords.isNullOrBlank()) Text("关键词：${entry?.keywords}")

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(imagesState.value, key = { it.id }) { img ->
                    val order = img.displayOrder ?: 0
                    val display = img.displayName ?: "第${order}页"
                    val a = analysisByImageId[img.id]
                    val preview = parsePreview(json, a?.extractedJson)

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${order}-${display}.jpg")
                        if (!preview.section.isNullOrBlank()) Text("主题：${preview.section}")
                        if (preview.mainPoints.isNotEmpty()) {
                            Text("要点：")
                            for (p in preview.mainPoints.take(5)) Text("• $p")
                        }
                        if (preview.citations.isNotEmpty()) {
                            Text("论文线索：")
                            for (c in preview.citations.take(3)) Text("• $c")
                        }
                    }
                }
            }
        }
    }
}

private fun parsePreview(json: Json, raw: String?): SlidePreview {
    if (raw.isNullOrBlank()) return SlidePreview(null, emptyList(), emptyList())
    val root =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return SlidePreview(null, emptyList(), emptyList())

    val section = root["section"]?.jsonPrimitive?.contentOrNull()
    val mainPoints =
        root["main_points"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList()
    val citations =
        root["citations"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList()
    return SlidePreview(section, mainPoints, citations)
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? {
    return runCatching { content }.getOrNull()
}

