package com.lzt.summaryofslides.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val providerPreset = stringPreferencesKey("provider_preset")
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val generalModel = stringPreferencesKey("general_model")
        val visionModel = stringPreferencesKey("vision_model")
    }

    val modelSettings: Flow<ModelSettings> = context.dataStore.data.map { prefs ->
        val preset = prefs[Keys.providerPreset]?.let { raw ->
            ModelProviderPreset.entries.firstOrNull { it.name == raw }
        } ?: ModelProviderPreset.ZhiPu
        ModelSettings(
            providerPreset = preset,
            baseUrl = prefs[Keys.baseUrl] ?: preset.defaultBaseUrl,
            apiKey = prefs[Keys.apiKey] ?: "",
            generalModel = prefs[Keys.generalModel] ?: preset.defaultGeneralModel,
            visionModel = prefs[Keys.visionModel] ?: preset.defaultVisionModel,
        )
    }

    suspend fun applyPreset(preset: ModelProviderPreset) {
        context.dataStore.edit { prefs ->
            prefs[Keys.providerPreset] = preset.name
            prefs[Keys.baseUrl] = preset.defaultBaseUrl
            prefs[Keys.generalModel] = preset.defaultGeneralModel
            prefs[Keys.visionModel] = preset.defaultVisionModel
        }
    }

    suspend fun update(
        baseUrl: String? = null,
        apiKey: String? = null,
        generalModel: String? = null,
        visionModel: String? = null,
    ) {
        context.dataStore.edit { prefs ->
            if (baseUrl != null) prefs[Keys.baseUrl] = baseUrl
            if (apiKey != null) prefs[Keys.apiKey] = apiKey
            if (generalModel != null) prefs[Keys.generalModel] = generalModel
            if (visionModel != null) prefs[Keys.visionModel] = visionModel
        }
    }
}
