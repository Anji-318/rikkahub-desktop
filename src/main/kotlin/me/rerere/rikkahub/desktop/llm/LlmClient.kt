package me.rerere.rikkahub.desktop.llm

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.desktop.data.ChatMessage
import me.rerere.rikkahub.desktop.data.KVEntry
import me.rerere.rikkahub.desktop.data.ProviderConfig

/** 流式增量：正文 / 思考链 / token 用量 */
sealed class StreamDelta {
    data class Content(val text: String) : StreamDelta()
    data class Reasoning(val text: String) : StreamDelta()
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamDelta()
}

/** 统一聊天请求参数 */
data class ChatParams(
    val model: String,
    val history: List<ChatMessage>,
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val contextSize: Int = 40,
    val reasoningEffort: String? = null, // none/low/medium/high/xhigh
    val searchContext: String? = null,
    val customHeaders: List<KVEntry> = emptyList(),
    val customBodies: List<KVEntry> = emptyList(),
)

/** 多协议 LLM 客户端统一接口（OpenAI / Claude / Gemini） */
interface LlmClient {
    fun streamChat(provider: ProviderConfig, params: ChatParams): Flow<StreamDelta>

    /** 非流式短调用（标题/建议/翻译），失败返回 null */
    suspend fun complete(provider: ProviderConfig, model: String, prompt: String): String?

    /** 拉取模型列表，失败返回空列表 */
    suspend fun listModels(provider: ProviderConfig): List<String>

    companion object {
        fun of(provider: ProviderConfig): LlmClient = when (provider.type) {
            "claude" -> ClaudeClient()
            "google" -> GeminiClient()
            else -> OpenAiClient()
        }
    }
}

/** 推理力度 → 思考预算 token（对齐安卓 ReasoningLevel.budgetTokens） */
fun reasoningBudgetOf(effort: String?): Int? = when (effort) {
    "none" -> 0
    "low" -> 1_000
    "medium" -> 2_000
    "high" -> 8_000
    "xhigh" -> 16_000
    else -> null // auto：不设置
}
