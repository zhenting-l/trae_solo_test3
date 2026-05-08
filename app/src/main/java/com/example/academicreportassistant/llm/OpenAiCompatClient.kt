package com.example.academicreportassistant.llm

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiCompatClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build(),
) {
    suspend fun visionChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        jpegBytes: ByteArray,
    ): String {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$b64"
        val body = buildChatCompletionBody(
            model = model,
            messages = buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive(prompt))
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("image_url"))
                                        put(
                                            "image_url",
                                            buildJsonObject { put("url", JsonPrimitive(dataUrl)) },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        return postForAssistantText(baseUrl, apiKey, body)
    }

    suspend fun textChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
    ): String {
        val body = buildChatCompletionBody(
            model = model,
            messages = buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(prompt))
                    },
                )
            },
        )
        return postForAssistantText(baseUrl, apiKey, body)
    }

    private fun buildChatCompletionBody(model: String, messages: JsonArray): JsonObject {
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", messages)
            put("temperature", JsonPrimitive(0.2))
        }
    }

    private suspend fun postForAssistantText(baseUrl: String, apiKey: String, body: JsonObject): String {
        val url = normalizeChatCompletionsUrl(baseUrl)
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

        return extractAssistantContent(raw)
    }

    private fun normalizeChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val lower = trimmed.lowercase()
        val isZhiPuV4 = lower.endsWith("/api/paas/v4") || lower.contains("/api/paas/v4/")
        if (isZhiPuV4) {
            return "$trimmed/chat/completions"
        }
        val hasV1 = lower.endsWith("/v1")
        val prefix = if (hasV1) trimmed else "$trimmed/v1"
        return "$prefix/chat/completions"
    }

    private fun extractAssistantContent(rawJson: String): String {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val choices = root["choices"]?.jsonArray ?: throw IllegalStateException("Missing choices")
        val first = choices.firstOrNull()?.jsonObject ?: throw IllegalStateException("Empty choices")
        val message = first["message"]?.jsonObject ?: throw IllegalStateException("Missing message")
        val content = message["content"]
        return when (content) {
            is JsonPrimitive -> content.content
            else -> content?.toString() ?: ""
        }
    }
}
