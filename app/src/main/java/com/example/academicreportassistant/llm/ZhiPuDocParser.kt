package com.lzt.summaryofslides.llm

import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class ZhiPuDocParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build(),
) {
    suspend fun parsePdfToMarkdown(
        baseUrl: String,
        apiKey: String,
        pdfFile: File,
        startPageId: Int? = null,
        endPageId: Int? = null,
    ): String {
        val apiPrefix = normalizeZhiPuApiPrefix(baseUrl)
        val url = "$apiPrefix/layout_parsing"
        val maxBytes = if (pdfFile.extension.equals("pdf", ignoreCase = true)) 50L * 1024L * 1024L else 10L * 1024L * 1024L
        if (pdfFile.length() > maxBytes) {
            throw IllegalStateException("文件过大：${pdfFile.name}")
        }
        val b64 = encodeBase64NoWrap(pdfFile)
        val dataUrl = "${mimePrefix(pdfFile)}$b64"
        val body =
            buildJsonObject {
                put("model", JsonPrimitive("glm-ocr"))
                put("file", JsonPrimitive(dataUrl))
                if (startPageId != null) put("start_page_id", JsonPrimitive(startPageId))
                if (endPageId != null) put("end_page_id", JsonPrimitive(endPageId))
            }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val req =
            Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(mediaType))
                .build()

        val raw =
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: $bodyStr")
                    }
                    bodyStr
                }
            }
        val root = json.parseToJsonElement(raw).jsonObject
        return root["md_results"]?.jsonPrimitive?.content ?: throw IllegalStateException("Missing md_results")
    }

    private fun mimePrefix(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "pdf" -> "data:application/pdf;base64,"
            "png" -> "data:image/png;base64,"
            "jpg", "jpeg" -> "data:image/jpeg;base64,"
            else -> "data:application/octet-stream;base64,"
        }
    }

    private suspend fun encodeBase64NoWrap(file: File): String {
        return withContext(Dispatchers.IO) {
            val baos = ByteArrayOutputStream()
            Base64OutputStream(baos, Base64.NO_WRAP).use { b64 ->
                FileInputStream(file).use { input ->
                    input.copyTo(b64)
                }
            }
            baos.toString(Charsets.UTF_8.name())
        }
    }

    private fun normalizeZhiPuApiPrefix(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val idxA = trimmed.indexOf("/api/paas/v4")
        if (idxA >= 0) return trimmed.substring(0, idxA) + "/api/paas/v4"
        val idxB = trimmed.indexOf("/api/coding/paas/v4")
        if (idxB >= 0) return trimmed.substring(0, idxB) + "/api/coding/paas/v4"
        throw IllegalStateException("Unsupported baseUrl for layout_parsing: $trimmed")
    }
}
