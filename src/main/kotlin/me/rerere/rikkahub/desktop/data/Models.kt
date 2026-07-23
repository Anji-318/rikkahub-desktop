package me.rerere.rikkahub.desktop.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** 键值对（自定义请求头 / 请求体 / 助手正则等复用） */
@Serializable
data class KVEntry(
    val key: String = "",
    val value: String = "",
)

/** 助手正则变换规则（对齐安卓 AssistantRegex 的子集） */
@Serializable
data class AssistantRegex(
    val id: String = UUID.randomUUID().toString(),
    val find: String = "",
    val replace: String = "",
    val scope: String = "output", // input=发送前作用于用户输入 / output=作用于 AI 回复
    val enabled: Boolean = true,
)

@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val models: List<String> = emptyList(),
    val enabled: Boolean = true,
    val type: String = "openai", // openai / claude / google
)

@Serializable
data class Assistant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String = "",
    val chatModel: String? = null, // 绑定模型（null 用全局 activeModel）
    val temperature: Double? = null, // null 用全局温度
    val topP: Double? = null, // null 不传（用 API 默认）
    val maxTokens: Int? = null, // null 不传
    val contextMessageSize: Int? = null, // 上下文消息条数，null 用默认 40
    val reasoningEffort: String? = null, // null=不设置；none/low/medium/high/xhigh
    val regexes: List<AssistantRegex> = emptyList(), // 正则变换
    val customHeaders: List<KVEntry> = emptyList(), // 自定义请求头
    val customBodies: List<KVEntry> = emptyList(), // 自定义请求体（value 优先按 JSON 解析）
)

/** 界面显示设置（对齐安卓 DisplaySetting 的子集） */
@Serializable
data class DisplaySetting(
    val fontSizeRatio: Float = 1.0f, // 字号倍率 0.5~2.0
    val showDateTimeInMessage: Boolean = true, // 消息时间戳
    val showTokenUsage: Boolean = true, // token 用量行
    val showThinkingContent: Boolean = true, // 思考链
    val autoCloseThinking: Boolean = true, // 思考链默认折叠
    val showModelIcon: Boolean = true, // 模型头像
    val showModelName: Boolean = true, // 模型名
)

@Serializable
data class QuickMessage(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
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
    val quickMessages: MutableList<QuickMessage> = mutableListOf(), // 快捷消息（输入栏插入）
    var darkTheme: Boolean? = null, // null=跟随系统
    var themeId: String = "rikka", // 预设主题 id，见 ui/theme/PresetTheme.kt
    var userNickname: String = "用户", // 用户昵称（侧栏与用户消息显示）
    var userAvatar: String = "", // 用户头像（data URL，空为默认首字符头像）
    var displaySetting: DisplaySetting = DisplaySetting(), // 界面显示设置
    // 默认模型分配（null 跟随聊天模型）
    var titleModelId: String? = null, // 标题生成
    var suggestionModelId: String? = null, // 对话建议
    var translateModelId: String? = null, // 翻译
    var titlePrompt: String = "为以下对话生成一个简短标题（不超过15个字），只输出标题本身：",
    var suggestionPrompt: String = "基于以下对话，给出4条用户接下来可能发送的简短回复（每条不超过20字），每行一条，不要编号、不要解释：",
    var translatePrompt: String = "把以下内容翻译成目标语言（中文内容翻译成英文，其他语言翻译成中文），只输出译文：",
    // 联网搜索
    var searchEnabled: Boolean = false,
    var searchService: String = "tavily", // tavily / exa
    var searchApiKey: String = "",
    var searchResultSize: Int = 5,
    /**
     * 修订号：每次设置变更 +1。AppSettings 是原地修改的，
     * 没有它 StateFlow 会因 equals 相等而吞掉更新（UI 不刷新）。
     */
    @kotlinx.serialization.Transient
    var rev: Int = 0,
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
    val imageUrls: List<String> = emptyList(), // 图片附件（data URL）
    val generationMs: Long? = null, // 生成耗时
    val reasoningMs: Long? = null, // 思考耗时
    val favorite: Boolean = false, // 收藏标记
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
    var chatSuggestions: List<String> = emptyList(), // 对话建议（生成后推荐快捷回复）
) {
    /** 当前分支下的消息序列 */
    val currentMessages: List<ChatMessage>
        get() = messageNodes.mapNotNull { it.currentMessage }
}
