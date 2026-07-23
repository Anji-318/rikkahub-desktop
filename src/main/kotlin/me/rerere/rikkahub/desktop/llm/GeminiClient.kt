package me.rerere.rikkahub.desktop.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.desktop.data.ChatMessage
import me.rerere.rikkahub.desktop.data.ProviderConfig

/** Gemini (Google) 原生协议客户端（SSE） */
class GeminiClient : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine { requestTimeout = 300_000 }
    }

    override fun streamChat(provider: ProviderConfig, params: ChatParams): Flow<StreamDelta> = flow {
        val base = provider.baseUrl.trimEnd('/')
        val url = "$base/v1beta/models/${params.model}:streamGenerateContent?alt=sse"
        val budget = reasoningBudgetOf(params.reasoningEffort)
        val system = listOfNotNull(
            params.systemPrompt.takeIf { it.isNotBlank() },
            params.searchContext?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n")
        val body = buildJsonObject {
            if (system.isNotBlank()) {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", system) })
                    })
                })
            }
            put("contents", buildJsonArray {
                params.history.takeLast(params.contextSize).forEach { msg -> add(buildContent(msg)) }
            })
            put("generationConfig", buildJsonObject {
                put("temperature", params.temperature)
                params.topP?.let { put("topP", it) }
                params.maxTokens?.let { put("maxOutputTokens", it) }
                if (budget != null) {
                    put("thinkingConfig", buildJsonObject {
                        put("thinkingBudget", budget)
                        if (budget > 0) put("includeThoughts", true)
                    })
                }
            })
            params.customBodies.forEach { kv ->
                put(kv.key, runCatching { json.parseToJsonElement(kv.value) }.getOrElse { JsonPrimitive(kv.value) })
            }
        }

        client.preparePost(url) {
            header("x-goog-api-key", provider.apiKey)
            contentType(ContentType.Application.Json)
            params.customHeaders.forEach { header(it.key, it.value) }
            setBody(body.toString())
        }.execute { resp ->
            if (resp.status.value >= 400) {
                val detail = runCatching { resp.bodyAsText().take(300) }.getOrDefault("")
                error("LLM 请求失败 HTTP ${resp.status.value} $detail")
            }
            val channel = resp.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.forEach { part ->
                            val p = part.jsonObject
                            val text = p["text"]?.jsonPrimitive?.contentOrNull
                            if (!text.isNullOrEmpty()) {
                                if (p["thought"]?.jsonPrimitive?.contentOrNull == "true") {
                                    emit(StreamDelta.Reasoning(text))
                                } else {
                                    emit(StreamDelta.Content(text))
                                }
                            }
                        }
                    obj["usageMetadata"]?.jsonObject?.let { usage ->
                        emit(
                            StreamDelta.Usage(
                                promptTokens = usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                                completionTokens = usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0,
                            )
                        )
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** role: user→"user"、assistant→"model"；图片转 inline_data */
    private fun buildContent(msg: ChatMessage): JsonObject = buildJsonObject {
        put("role", if (msg.role == "assistant") "model" else msg.role)
        put("parts", buildJsonArray {
            if (msg.content.isNotBlank()) {
                add(buildJsonObject { put("text", msg.content) })
            }
            msg.imageUrls.forEach { url ->
                // data:<mime>;base64,<data>
                val mime = url.substringAfter("data:").substringBefore(";")
                val data = url.substringAfter(",", "")
                add(buildJsonObject {
                    put("inline_data", buildJsonObject {
                        put("mime_type", mime)
                        put("data", data)
                    })
                })
            }
        })
    }

    /** 非流式短调用（标题/建议/翻译），失败返回 null */
    override suspend fun complete(provider: ProviderConfig, model: String, prompt: String): String? = runCatching {
        val base = provider.baseUrl.trimEnd('/')
        val url = "$base/v1beta/models/$model:generateContent"
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
        }
        val resp = client.post(url) {
            header("x-goog-api-key", provider.apiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (resp.status.value >= 400) return null
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.filter { it.jsonObject["thought"]?.jsonPrimitive?.contentOrNull != "true" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
    }.getOrNull()

    /** 拉取模型列表（GET /v1beta/models），失败时返回空列表 */
    override suspend fun listModels(provider: ProviderConfig): List<String> = runCatching {
        val resp = client.get(provider.baseUrl.trimEnd('/') + "/v1beta/models") {
            header("x-goog-api-key", provider.apiKey)
        }
        if (resp.status.value >= 400) return emptyList()
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["models"]
            ?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/")
            } ?: emptyList()
    }.getOrElse { emptyList() }
}
