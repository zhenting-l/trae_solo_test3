package com.lzt.summaryofslides.llm

import android.util.Base64
import android.util.Base64OutputStream
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
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
        temperature: Double = 0.2,
        topP: Double? = null,
        maxTokens: Int? = null,
    ): String {
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$b64"
        return visionChatCompletionWithDataUrls(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            prompt = prompt,
            dataUrls = listOf(dataUrl),
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
    }

    suspend fun visionChatCompletionWithJpegBytesList(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        jpegBytesList: List<ByteArray>,
        temperature: Double = 0.2,
        topP: Double? = null,
        maxTokens: Int? = null,
    ): String {
        val urls =
            jpegBytesList.map { bytes ->
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$b64"
            }
        return visionChatCompletionWithDataUrls(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            prompt = prompt,
            dataUrls = urls,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
    }

    suspend fun visionChatCompletionWithPdfFiles(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        pdfFiles: List<File>,
        temperature: Double = 0.2,
        topP: Double? = null,
        maxTokens: Int? = null,
    ): String {
        val maxBytes = 15L * 1024L * 1024L
        val urls =
            pdfFiles.map { file ->
                if (!file.exists()) throw IllegalStateException("PDF不存在：${file.name}")
                if (file.length() > maxBytes) throw IllegalStateException("PDF过大：${file.name}")
                val b64 = encodeBase64NoWrap(file)
                "data:application/pdf;base64,$b64"
            }
        return visionChatCompletionWithDataUrls(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            prompt = prompt,
            dataUrls = urls,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
    }

    private suspend fun visionChatCompletionWithDataUrls(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        dataUrls: List<String>,
        temperature: Double,
        topP: Double?,
        maxTokens: Int?,
    ): String {
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
                                for (url in dataUrls) {
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("image_url"))
                                            put(
                                                "image_url",
                                                buildJsonObject { put("url", JsonPrimitive(url)) },
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            },
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
        return postForAssistantText(baseUrl, apiKey, body)
    }

    suspend fun textChatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        temperature: Double = 0.2,
        topP: Double? = null,
        maxTokens: Int? = null,
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
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
        return postForAssistantText(baseUrl, apiKey, body)
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

    private fun buildChatCompletionBody(
        model: String,
        messages: JsonArray,
        temperature: Double,
        topP: Double?,
        maxTokens: Int?,
    ): JsonObject {
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", messages)
            put("temperature", JsonPrimitive(temperature))
            if (topP != null) put("top_p", JsonPrimitive(topP))
            if (maxTokens != null) put("max_tokens", JsonPrimitive(maxTokens))
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
        val isZhiPuCodingV4 = lower.endsWith("/api/coding/paas/v4") || lower.contains("/api/coding/paas/v4/")
        if (isZhiPuCodingV4) {
            return "$trimmed/chat/completions"
        }
        throw IllegalStateException("Unsupported baseUrl for GLM: $trimmed")
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
