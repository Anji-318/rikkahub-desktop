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
import kotlinx.serialization.json.JsonNull
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
import me.rerere.rikkahub.desktop.data.KVEntry
import me.rerere.rikkahub.desktop.data.ProviderConfig

/**
 * OpenAI 兼容协议流式客户端（SSE）。
 */
class OpenAiClient : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine { requestTimeout = 300_000 }
    }

    override fun streamChat(provider: ProviderConfig, params: ChatParams): Flow<StreamDelta> = flow {
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", params.model)
            put("temperature", params.temperature)
            params.topP?.let { put("top_p", it) }
            params.maxTokens?.let { put("max_tokens", it) }
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            params.reasoningEffort?.let { put("reasoning_effort", it) }
            put("messages", buildJsonArray {
                if (params.systemPrompt.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", params.systemPrompt)
                    })
                }
                params.searchContext?.let {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", it)
                    })
                }
                params.history.takeLast(params.contextSize).forEach { msg -> add(buildMessage(msg)) }
                // 工具循环续传：每个 exchange = assistant 工具调用消息 + role:tool 结果消息
                params.toolExchanges.forEach { ex ->
                    add(buildJsonObject {
                        put("role", "assistant")
                        put("content", JsonNull)
                        put("tool_calls", buildJsonArray {
                            add(buildJsonObject {
                                put("id", ex.callId)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", ex.name)
                                    put("arguments", ex.argumentsJson)
                                })
                            })
                        })
                    })
                    add(buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", ex.callId)
                        put("content", ex.result)
                    })
                }
            })
            // 可用工具：OpenAI 格式 {type:"function", function:{name,description,parameters}}
            if (params.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    params.tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", json.parseToJsonElement(t.parametersSchema))
                            })
                        })
                    }
                })
            }
            putCustomBodies(params.customBodies)
        }

        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
            params.customHeaders.forEach { header(it.key, it.value) }
            setBody(body.toString())
        }.execute { resp ->
            if (resp.status.value >= 400) {
                val detail = runCatching { resp.bodyAsText().take(300) }.getOrDefault("")
                error("LLM 请求失败 HTTP ${resp.status.value} $detail")
            }
            val channel = resp.bodyAsChannel()
            // 流式 tool_calls 按 index 聚合（id/name 首帧到达，arguments 字符串逐帧拼接）
            val toolCalls = sortedMapOf<Int, Triple<String, String, StringBuilder>>()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    val delta = choice?.get("delta")?.jsonObject
                    val reasoning = (delta?.get("reasoning_content") ?: delta?.get("reasoning"))
                        ?.jsonPrimitive?.contentOrNull
                    if (!reasoning.isNullOrEmpty()) emit(StreamDelta.Reasoning(reasoning))
                    val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
                    if (!content.isNullOrEmpty()) emit(StreamDelta.Content(content))
                    delta?.get("tool_calls")?.jsonArray?.forEach { tc ->
                        val o = tc.jsonObject
                        val idx = o["index"]?.jsonPrimitive?.intOrNull ?: 0
                        val fn = o["function"]?.jsonObject
                        val existing = toolCalls[idx]
                        val id = o["id"]?.jsonPrimitive?.contentOrNull ?: existing?.first ?: ""
                        val name = fn?.get("name")?.jsonPrimitive?.contentOrNull ?: existing?.second ?: ""
                        val args = existing?.third ?: StringBuilder()
                        fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { args.append(it) }
                        toolCalls[idx] = Triple(id, name, args)
                    }
                    // finish_reason == "tool_calls" 时聚合完毕，整体发射一次
                    if (choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull == "tool_calls" && toolCalls.isNotEmpty()) {
                        toolCalls.forEach { (_, v) ->
                            emit(StreamDelta.ToolCall(v.first, v.second, v.third.toString()))
                        }
                        toolCalls.clear()
                    }
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

    override suspend fun complete(provider: ProviderConfig, model: String, prompt: String): String? = runCatching {
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

    override suspend fun listModels(provider: ProviderConfig): List<String> = runCatching {
        val resp = client.get(provider.baseUrl.trimEnd('/') + "/models") {
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
        }
        if (resp.status.value >= 400) return emptyList()
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["data"]
            ?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content } ?: emptyList()
    }.getOrElse { emptyList() }
}

/** 合并自定义请求体字段：value 优先按 JSON 解析，失败按字符串 */
internal fun JsonObjectBuilderScope.putCustomBodies(entries: List<KVEntry>) {
    val json = Json { ignoreUnknownKeys = true }
    entries.forEach { (key, value) ->
        if (key.isBlank()) return@forEach
        val parsed = runCatching { json.parseToJsonElement(value) }.getOrNull()
        if (parsed != null) put(key, parsed) else put(key, value)
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder
