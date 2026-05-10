package com.lzt.summaryofslides.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzt.summaryofslides.data.AppContainer
import com.lzt.summaryofslides.settings.ModelProviderPreset
import com.lzt.summaryofslides.settings.ModelSettings
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
                generalModel = ModelProviderPreset.ZhiPu.defaultGeneralModel,
                visionModel = ModelProviderPreset.ZhiPu.defaultVisionModel,
                enableWebEnrichment = false,
                preferWebViewMarkdown = true,
            ),
        )

    fun applyPreset(preset: ModelProviderPreset) {
        viewModelScope.launch { store.applyPreset(preset) }
    }

    fun save(
        baseUrl: String,
        apiKey: String,
        generalModel: String,
        visionModel: String,
        enableWebEnrichment: Boolean,
        preferWebViewMarkdown: Boolean,
    ) {
        viewModelScope.launch {
            store.update(
                baseUrl = baseUrl,
                apiKey = apiKey,
                generalModel = generalModel,
                visionModel = visionModel,
                enableWebEnrichment = enableWebEnrichment,
                preferWebViewMarkdown = preferWebViewMarkdown,
            )
        }
    }
}
