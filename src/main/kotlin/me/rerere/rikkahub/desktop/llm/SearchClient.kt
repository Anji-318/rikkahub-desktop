package me.rerere.rikkahub.desktop.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 联网搜索客户端（对齐安卓 search 模块的子集：Tavily / Exa）。
 * 返回格式化后的搜索上下文文本，失败返回 null。
 */
class SearchClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        engine { requestTimeout = 30_000 }
    }

    suspend fun search(service: String, apiKey: String, query: String, resultSize: Int): String? =
        runCatching {
            when (service) {
                "exa" -> searchExa(apiKey, query, resultSize)
                else -> searchTavily(apiKey, query, resultSize)
            }
        }.getOrNull()

    private data class Item(val title: String, val url: String, val text: String)

    private fun format(items: List<Item>): String? {
        if (items.isEmpty()) return null
        return buildString {
            append("以下是联网搜索结果，供回答时参考：\n")
            items.forEachIndexed { i, item ->
                append("[${i + 1}] ").append(item.title).append('\n')
                append(item.url).append('\n')
                append(item.text.take(500)).append("\n\n")
            }
        }.trim()
    }

    private suspend fun searchTavily(apiKey: String, query: String, resultSize: Int): String? {
        val body = buildJsonObject {
            put("query", query)
            put("max_results", resultSize)
            put("search_depth", "advanced")
            put("topic", "general")
        }
        val resp = client.post("https://api.tavily.com/search") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(body.toString())
        }
        if (resp.status.value >= 400) return null
        val results = json.parseToJsonElement(resp.bodyAsText()).jsonObject["results"]?.jsonArray ?: return null
        return format(results.mapNotNull { el ->
            val obj = el.jsonObject
            Item(
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                url = obj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                text = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        })
    }

    private suspend fun searchExa(apiKey: String, query: String, resultSize: Int): String? {
        val body = buildJsonObject {
            put("query", query)
            put("numResults", resultSize)
            put("contents", buildJsonObject { put("text", true) })
        }
        val resp = client.post("https://api.exa.ai/search") {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            setBody(body.toString())
        }
        if (resp.status.value >= 400) return null
        val results = json.parseToJsonElement(resp.bodyAsText()).jsonObject["results"]?.jsonArray ?: return null
        return format(results.mapNotNull { el ->
            val obj = el.jsonObject
            Item(
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                url = obj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                text = obj["text"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        })
    }
}
