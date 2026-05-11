package com.lzt.summaryofslides.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class ModelSettings(
    val providerPreset: ModelProviderPreset,
    val baseUrl: String,
    val apiKey: String,
    val generalModel: String,
    val visionModel: String,
    val enableWebEnrichment: Boolean,
    val preferWebViewMarkdown: Boolean,
    val pdfOcrFallbackEnabled: Boolean,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val generalModel = stringPreferencesKey("general_model")
        val visionModel = stringPreferencesKey("vision_model")
        val enableWebEnrichment = booleanPreferencesKey("enable_web_enrichment")
        val preferWebViewMarkdown = booleanPreferencesKey("prefer_webview_markdown")
        val pdfOcrFallbackEnabled = booleanPreferencesKey("pdf_ocr_fallback_enabled")
    }

    val modelSettings: Flow<ModelSettings> = context.dataStore.data.map { prefs ->
        val preset = ModelProviderPreset.ZhiPu
        ModelSettings(
            providerPreset = preset,
            baseUrl = preset.defaultBaseUrl,
            apiKey = prefs[Keys.apiKey] ?: "",
            generalModel = prefs[Keys.generalModel] ?: preset.defaultGeneralModel,
            visionModel = prefs[Keys.visionModel] ?: preset.defaultVisionModel,
            enableWebEnrichment = prefs[Keys.enableWebEnrichment] ?: false,
            preferWebViewMarkdown = prefs[Keys.preferWebViewMarkdown] ?: true,
            pdfOcrFallbackEnabled = prefs[Keys.pdfOcrFallbackEnabled] ?: true,
        )
    }

    suspend fun applyPreset(preset: ModelProviderPreset) {
        context.dataStore.edit { prefs ->
            prefs[Keys.generalModel] = preset.defaultGeneralModel
            prefs[Keys.visionModel] = preset.defaultVisionModel
        }
    }

    suspend fun update(
        apiKey: String? = null,
        generalModel: String? = null,
        visionModel: String? = null,
        enableWebEnrichment: Boolean? = null,
        preferWebViewMarkdown: Boolean? = null,
        pdfOcrFallbackEnabled: Boolean? = null,
    ) {
        context.dataStore.edit { prefs ->
            if (apiKey != null) prefs[Keys.apiKey] = apiKey
            if (generalModel != null) prefs[Keys.generalModel] = generalModel
            if (visionModel != null) prefs[Keys.visionModel] = visionModel
            if (enableWebEnrichment != null) prefs[Keys.enableWebEnrichment] = enableWebEnrichment
            if (preferWebViewMarkdown != null) prefs[Keys.preferWebViewMarkdown] = preferWebViewMarkdown
            if (pdfOcrFallbackEnabled != null) prefs[Keys.pdfOcrFallbackEnabled] = pdfOcrFallbackEnabled
        }
    }
}
