package com.lzt.summaryofslides.ui.shareimport

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SharePayload(
    val uris: List<Uri>,
)

object SharePayloadHolder {
    private val _payload = MutableStateFlow<SharePayload?>(null)
    val payload: StateFlow<SharePayload?> = _payload

    fun set(payload: SharePayload?) {
        _payload.value = payload
    }

    fun consume(): SharePayload? {
        val p = _payload.value
        _payload.value = null
        return p
    }
}
