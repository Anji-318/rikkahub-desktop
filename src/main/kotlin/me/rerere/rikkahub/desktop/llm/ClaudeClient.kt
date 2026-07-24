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

/** Claude (Anthropic) 原生协议客户端（SSE） */
class ClaudeClient : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        engine { requestTimeout = 300_000 }
    }

    /** baseUrl 已带 /v1 则直接拼路径，否则补 /v1 */
    private fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith("/v1")) "$base/$path" else "$base/v1/$path"
    }

    override fun streamChat(provider: ProviderConfig, params: ChatParams): Flow<StreamDelta> = flow {
        val url = endpoint(provider.baseUrl, "messages")
        val budget = reasoningBudgetOf(params.reasoningEffort)
        val system = listOfNotNull(
            params.systemPrompt.takeIf { it.isNotBlank() },
            params.searchContext?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n")
        val body = buildJsonObject {
            put("model", params.model)
            put("max_tokens", params.maxTokens ?: 8192)
            if (system.isNotBlank()) put("system", system)
            if (budget != null && budget > 0) {
                // 开启 thinking 时 Anthropic 不允许传 temperature / top_p
                put("thinking", buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", budget)
                })
            } else {
                put("temperature", params.temperature)
                params.topP?.let { put("top_p", it) }
            }
            put("messages", buildJsonArray {
                params.history.takeLast(params.contextSize).forEach { msg -> add(buildMessage(msg)) }
                // 工具循环续传：assistant 消息（tool_use 块）+ user 消息（tool_result 块）
                params.toolExchanges.forEach { ex ->
                    add(buildJsonObject {
                        put("role", "assistant")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "tool_use")
                                put("id", ex.callId)
                                put("name", ex.name)
                                put("input", runCatching { json.parseToJsonElement(ex.argumentsJson) }
                                    .getOrElse { buildJsonObject {} })
                            })
                        })
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", ex.callId)
                                put("content", ex.result)
                            })
                        })
                    })
                }
            })
            // 可用工具：Claude 格式 {name, description, input_schema}
            if (params.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    params.tools.forEach { t ->
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", json.parseToJsonElement(t.parametersSchema))
                        })
                    }
                })
            }
            put("stream", true)
            params.customBodies.forEach { kv ->
                put(kv.key, runCatching { json.parseToJsonElement(kv.value) }.getOrElse { JsonPrimitive(kv.value) })
            }
        }

        client.preparePost(url) {
            header("x-api-key", provider.apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            params.customHeaders.forEach { header(it.key, it.value) }
            setBody(body.toString())
        }.execute { resp ->
            if (resp.status.value >= 400) {
                val detail = runCatching { resp.bodyAsText().take(300) }.getOrDefault("")
                error("LLM 请求失败 HTTP ${resp.status.value} $detail")
            }
            var inputTokens: Int? = null
            // 流式 tool_use 聚合：content_block_start 拿 id/name，input_json_delta 拼 partial_json，block_stop 发射
            var toolUseId: String? = null
            var toolUseName: String? = null
            val toolUseArgs = StringBuilder()
            val channel = resp.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "content_block_start" -> {
                            val block = obj["content_block"]?.jsonObject
                            if (block?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") {
                                toolUseId = block["id"]?.jsonPrimitive?.contentOrNull
                                toolUseName = block["name"]?.jsonPrimitive?.contentOrNull
                                toolUseArgs.clear()
                            }
                        }

                        "content_block_delta" -> {
                            val delta = obj["delta"]?.jsonObject
                            when (delta?.get("type")?.jsonPrimitive?.contentOrNull) {
                                "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { emit(StreamDelta.Content(it)) }

                                "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let { emit(StreamDelta.Reasoning(it)) }

                                "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.contentOrNull
                                    ?.let { toolUseArgs.append(it) }
                            }
                        }

                        "content_block_stop" -> {
                            val id = toolUseId
                            val name = toolUseName
                            if (id != null && name != null) {
                                emit(StreamDelta.ToolCall(id, name, toolUseArgs.toString().ifBlank { "{}" }))
                            }
                            toolUseId = null
                            toolUseName = null
                            toolUseArgs.clear()
                        }

                        "message_start" -> {
                            inputTokens = obj["message"]?.jsonObject
                                ?.get("usage")?.jsonObject
                                ?.get("input_tokens")?.jsonPrimitive?.intOrNull
                        }

                        "message_delta" -> {
                            val outputTokens = obj["usage"]?.jsonObject
                                ?.get("output_tokens")?.jsonPrimitive?.intOrNull
                            val input = inputTokens
                            if (input != null && outputTokens != null) {
                                emit(StreamDelta.Usage(input, outputTokens))
                            }
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** 有图片附件时走 content 数组（base64 source），否则纯文本 */
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
                    // data:<mime>;base64,<data>
                    val mime = url.substringAfter("data:").substringBefore(";")
                    val data = url.substringAfter(",", "")
                    add(buildJsonObject {
                        put("type", "image")
                        put("source", buildJsonObject {
                            put("type", "base64")
                            put("media_type", mime)
                            put("data", data)
                        })
                    })
                }
            })
        }
    }

    /** 非流式短调用（标题/建议/翻译），失败返回 null */
    override suspend fun complete(provider: ProviderConfig, model: String, prompt: String): String? = runCatching {
        val url = endpoint(provider.baseUrl, "messages")
        val body = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        val resp = client.post(url) {
            header("x-api-key", provider.apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (resp.status.value >= 400) return null
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["content"]?.jsonArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
    }.getOrNull()

    /** 拉取模型列表（GET /v1/models），失败时返回空列表 */
    override suspend fun listModels(provider: ProviderConfig): List<String> = runCatching {
        val resp = client.get(endpoint(provider.baseUrl, "models")) {
            header("x-api-key", provider.apiKey)
            header("anthropic-version", "2023-06-01")
        }
        if (resp.status.value >= 400) return emptyList()
        json.parseToJsonElement(resp.bodyAsText()).jsonObject["data"]
            ?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content } ?: emptyList()
    }.getOrElse { emptyList() }
}
