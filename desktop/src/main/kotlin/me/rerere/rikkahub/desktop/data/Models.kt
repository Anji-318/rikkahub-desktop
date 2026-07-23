package me.rerere.rikkahub.desktop.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<String> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class Assistant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String = "",
    val chatModel: String? = null, // 绑定模型（null 用全局 activeModel）
    val temperature: Double? = null, // null 用全局温度
)

@Serializable
data class AppSettings(
    val providers: MutableList<ProviderConfig> = mutableListOf(),
    var activeProviderId: String? = null,
    var activeModel: String? = null,
    var systemPrompt: String = "",
    var temperature: Double = 0.7,
    val assistants: MutableList<Assistant> = mutableListOf(),
    var activeAssistantId: String? = null,
    var darkTheme: Boolean? = null, // null=跟随系统
    var themeId: String = "rikka", // 预设主题 id，见 ui/theme/PresetTheme.kt
) {
    fun activeAssistant(): Assistant? =
        assistants.firstOrNull { it.id == activeAssistantId } ?: assistants.firstOrNull()
}

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val model: String? = null,
    val reasoning: String = "",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
)

/**
 * 消息节点：一个节点可包含多条候选消息（分支），
 * selectIndex 指向当前选中的分支。与 Android 版 MessageNode 对齐。
 */
@Serializable
data class MessageNode(
    val id: String = UUID.randomUUID().toString(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var selectIndex: Int = 0,
) {
    val currentMessage: ChatMessage? get() = messages.getOrNull(selectIndex)
}

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新对话",
    val messageNodes: MutableList<MessageNode> = mutableListOf(),
    val assistantId: String? = null,
    var pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
) {
    /** 当前分支下的消息序列 */
    val currentMessages: List<ChatMessage>
        get() = messageNodes.mapNotNull { it.currentMessage }
}
