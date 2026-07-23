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
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

/** 流式增量：正文 / 思考链 / token 用量 */
sealed class StreamDelta {
    data class Content(val text: String) : StreamDelta()
    data class Reasoning(val text: String) : StreamDelta()
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamDelta()
}

/**
 * OpenAI 兼容协议流式客户端（SSE）。
 * Phase 2 将替换为上游 :ai 模块的完整 Provider 体系（Claude/Gemini 原生协议）。
 */
class OpenAiClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine { requestTimeout = 300_000 }
    }

    fun streamChat(
        provider: ProviderConfig,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        temperature: Double,
        reasoningEffort: String? = null,
        topP: Double? = null,
        maxTokens: Int? = null,
        contextSize: Int = 40,
        searchContext: String? = null,
    ): Flow<StreamDelta> = flow {
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            topP?.let { put("top_p", it) }
            maxTokens?.let { put("max_tokens", it) }
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            reasoningEffort?.let { put("reasoning_effort", it) }
            put("messages", buildJsonArray {
                if (systemPrompt.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                searchContext?.let {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", it)
                    })
                }
                history.takeLast(contextSize).forEach { msg -> add(buildMessage(msg)) }
            })
        }

        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
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
                if (data == "[DONE]") break
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    val delta = obj["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("delta")?.jsonObject
                    val reasoning = (delta?.get("reasoning_content") ?: delta?.get("reasoning"))
                        ?.jsonPrimitive?.contentOrNull
                    if (!reasoning.isNullOrEmpty()) emit(StreamDelta.Reasoning(reasoning))
                    val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                    if (!content.isNullOrEmpty()) emit(StreamDelta.Content(content))
                    obj["usage"]?.jsonObject?.let { usage ->
                        emit(
                            StreamDelta.Usage(
                                promptTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                                completionTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                            )
                        )
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 有图片附件时走多模态 content 数组，否则纯文本 */
    private fun buildMessage(msg: ChatMessage): JsonObject = buildJsonObject {
        put("role", msg.role)
        if (msg.imageUrls.isEmpty()) {
            put("content", msg.content)
        } else {
            put("content", buildJsonArray {
                if (msg.content.isNotBlank()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", msg.content)
                    })
                }
                msg.imageUrls.forEach { url ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", url) })
                    })
                }
            })
        }
    }

    /** 非流式短调用（生成对话建议等场景），失败返回 null */
    suspend fun complete(provider: ProviderConfig, model: String, prompt: String): String? = runCatching {
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        val resp = client.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
            setBody(body.toString())
        }
        if (resp.status.value >= 400) return null
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** 拉取模型列表（GET /models），失败时返回空列表 */
    suspend fun listModels(provider: ProviderConfig): List<String> = runCatching {
        val resp = client.get(provider.baseUrl.trimEnd('/') + "/models") {
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
        }
        if (resp.status.value >= 400) return emptyList()
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["data"]
            ?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content } ?: emptyList()
    }.getOrElse { emptyList() }
}
