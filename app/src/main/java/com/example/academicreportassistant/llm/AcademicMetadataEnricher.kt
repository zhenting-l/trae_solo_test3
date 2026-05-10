package com.lzt.summaryofslides.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ExternalPaperMetadata(
    val title: String?,
    val authors: List<String>,
    val venue: String?,
    val year: Int?,
    val doi: String?,
    val url: String?,
    val keywords: List<String>,
    val source: String,
    val confidence: Double,
) {
    fun toPromptBlock(): String {
        val lines = mutableListOf<String>()
        lines += "source=$source"
        lines += "confidence=$confidence"
        if (!title.isNullOrBlank()) lines += "title=$title"
        if (authors.isNotEmpty()) lines += "authors=${authors.joinToString(", ")}"
        if (!venue.isNullOrBlank()) lines += "venue=$venue"
        if (year != null) lines += "year=$year"
        if (!doi.isNullOrBlank()) lines += "doi=$doi"
        if (!url.isNullOrBlank()) lines += "url=$url"
        if (keywords.isNotEmpty()) lines += "keywords=${keywords.take(12).joinToString(", ")}"
        return lines.joinToString("\n")
    }
}

class AcademicMetadataEnricher(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    suspend fun enrichFromPdfMarkdown(pdfMd: String): ExternalPaperMetadata? {
        val doi = extractDoi(pdfMd)
        val arxivId = extractArxivId(pdfMd)
        val title = extractTitleCandidate(pdfMd)

        if (!doi.isNullOrBlank()) {
            fetchCrossrefByDoi(doi)?.let { return it }
        }

        if (!arxivId.isNullOrBlank()) {
            fetchArxivById(arxivId)?.let { return it }
        }

        if (!title.isNullOrBlank()) {
            fetchCrossrefByTitle(title)?.let { return it }
        }

        return null
    }

    private suspend fun fetchCrossrefByDoi(doi: String): ExternalPaperMetadata? {
        val url = ("https://api.crossref.org/works/" + doi.trim()).toHttpUrl()
        val body = httpGet(url, headers = emptyMap()) ?: return null
        val message = runCatching { json.parseToJsonElement(body).jsonObject["message"]?.jsonObject }.getOrNull() ?: return null
        val title = message["title"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        val urlField = message["URL"]?.jsonPrimitive?.content
        val year = extractCrossrefYear(message)
        val venue = message["container-title"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        val authors = extractCrossrefAuthors(message)
        val keywords = message["subject"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList()
        val conf = if (title.isNullOrBlank()) 0.6 else 1.0
        return ExternalPaperMetadata(
            title = title,
            authors = authors,
            venue = venue,
            year = year,
            doi = doi,
            url = urlField,
            keywords = keywords,
            source = "crossref",
            confidence = conf,
        )
    }

    private suspend fun fetchCrossrefByTitle(titleQuery: String): ExternalPaperMetadata? {
        val url =
            "https://api.crossref.org/works"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("query.title", titleQuery.take(300))
                .addQueryParameter("rows", "1")
                .build()
        val body = httpGet(url, headers = emptyMap()) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val item =
            root["message"]
                ?.jsonObject
                ?.get("items")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: return null
        val hitTitle = item["title"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: return null
        val conf = titleConfidence(titleQuery, hitTitle)
        if (conf < 0.6) return null
        val doi = item["DOI"]?.jsonPrimitive?.content
        val urlField = item["URL"]?.jsonPrimitive?.content
        val year = extractCrossrefYear(item)
        val venue = item["container-title"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        val authors = extractCrossrefAuthors(item)
        val keywords = item["subject"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList()
        return ExternalPaperMetadata(
            title = hitTitle,
            authors = authors,
            venue = venue,
            year = year,
            doi = doi,
            url = urlField,
            keywords = keywords,
            source = "crossref",
            confidence = conf,
        )
    }

    private suspend fun fetchArxivById(arxivId: String): ExternalPaperMetadata? {
        val url =
            "https://export.arxiv.org/api/query"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("search_query", "id:$arxivId")
                .addQueryParameter("max_results", "1")
                .build()
        val body = httpGet(url, headers = emptyMap()) ?: return null
        val title = Regex("<title>([\\s\\S]*?)</title>").findAll(body).drop(1).firstOrNull()?.groupValues?.getOrNull(1)?.trim()
        val authors = Regex("<name>([\\s\\S]*?)</name>").findAll(body).mapNotNull { it.groupValues.getOrNull(1)?.trim() }.toList()
        val urlField = Regex("<id>([\\s\\S]*?)</id>").findAll(body).drop(1).firstOrNull()?.groupValues?.getOrNull(1)?.trim()
        if (title.isNullOrBlank()) return null
        return ExternalPaperMetadata(
            title = title.replace(Regex("\\s+"), " "),
            authors = authors,
            venue = "arXiv",
            year = null,
            doi = null,
            url = urlField,
            keywords = emptyList(),
            source = "arxiv",
            confidence = 1.0,
        )
    }

    private suspend fun httpGet(url: okhttp3.HttpUrl, headers: Map<String, String>): String? {
        return withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder().url(url)
            for ((k, v) in headers) reqBuilder.header(k, v)
            val res = client.newCall(reqBuilder.build()).execute()
            res.use { r ->
                if (!r.isSuccessful) return@withContext null
                return@withContext r.body?.string()
            }
        }
    }

    private fun extractCrossrefAuthors(obj: kotlinx.serialization.json.JsonObject): List<String> {
        val arr = obj["author"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { a ->
            val o = a.jsonObject
            val family = o["family"]?.jsonPrimitive?.contentOrNull()
            val given = o["given"]?.jsonPrimitive?.contentOrNull()
            val name =
                when {
                    !given.isNullOrBlank() && !family.isNullOrBlank() -> "$given $family"
                    !family.isNullOrBlank() -> family
                    else -> null
                }
            name
        }
    }

    private fun extractCrossrefYear(obj: kotlinx.serialization.json.JsonObject): Int? {
        val parts =
            obj["published-print"]
                ?.jsonObject
                ?.get("date-parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.intOrNull
        if (parts != null) return parts
        return obj["created"]?.jsonObject?.get("date-parts")?.jsonArray?.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull
    }

    private fun extractDoi(text: String): String? {
        val m = Regex("""10\.\d{4,9}/[^\s"<>]+""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return m.value.trim().trimEnd('.', ',', ';')
    }

    private fun extractArxivId(text: String): String? {
        val m1 = Regex("""arXiv:\s*([0-9]{4}\.[0-9]{4,5})(v\d+)?""", RegexOption.IGNORE_CASE).find(text)
        if (m1 != null) return m1.groupValues[1]
        val m2 = Regex("""\b([0-9]{4}\.[0-9]{4,5})(v\d+)?\b""").find(text)
        return m2?.groupValues?.getOrNull(1)
    }

    private fun extractTitleCandidate(md: String): String? {
        val lines = md.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotBlank() }
        val header = lines.firstOrNull { it.startsWith("#") }?.trimStart('#')?.trim()
        if (!header.isNullOrBlank() && header.length in 8..200) return header
        val first = lines.firstOrNull()?.takeIf { it.length in 8..200 }
        return first
    }

    private fun titleConfidence(query: String, hit: String): Double {
        val q = tokenize(query)
        val h = tokenize(hit)
        if (q.isEmpty() || h.isEmpty()) return 0.0
        val inter = q.intersect(h).size.toDouble()
        val union = q.union(h).size.toDouble().coerceAtLeast(1.0)
        return inter / union
    }

    private fun tokenize(s: String): Set<String> {
        return s.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 }
            .toSet()
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? {
    return runCatching { this.content }.getOrNull()
}
