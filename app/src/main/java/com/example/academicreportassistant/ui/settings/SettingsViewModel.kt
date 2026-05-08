package com.example.academicreportassistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.academicreportassistant.data.AppContainer
import com.example.academicreportassistant.settings.ModelProviderPreset
import com.example.academicreportassistant.settings.ModelSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val store = AppContainer.settingsStore

    val settings: StateFlow<ModelSettings> =
        store.modelSettings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ModelSettings(
                providerPreset = ModelProviderPreset.ZhiPu,
                baseUrl = ModelProviderPreset.ZhiPu.defaultBaseUrl,
                apiKey = "",
                visionModel = ModelProviderPreset.ZhiPu.defaultVisionModel,
                textModel = ModelProviderPreset.ZhiPu.defaultTextModel,
            ),
        )

    fun applyPreset(preset: ModelProviderPreset) {
        viewModelScope.launch { store.applyPreset(preset) }
    }

    fun save(
        baseUrl: String,
        apiKey: String,
        visionModel: String,
        textModel: String,
    ) {
        viewModelScope.launch {
            store.update(
                baseUrl = baseUrl,
                apiKey = apiKey,
                visionModel = visionModel,
                textModel = textModel,
            )
        }
    }
}

