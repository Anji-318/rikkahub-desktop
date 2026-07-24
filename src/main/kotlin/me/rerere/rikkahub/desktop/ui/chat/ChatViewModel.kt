package me.rerere.rikkahub.desktop.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.desktop.data.AppSettings
import me.rerere.rikkahub.desktop.data.Backup
import me.rerere.rikkahub.desktop.data.ChatMessage
import me.rerere.rikkahub.desktop.data.Conversation
import me.rerere.rikkahub.desktop.data.ConversationStore
import me.rerere.rikkahub.desktop.data.GLOBAL_MEMORY_ID
import me.rerere.rikkahub.desktop.data.MemoryEntry
import me.rerere.rikkahub.desktop.data.MemoryStore
import me.rerere.rikkahub.desktop.data.MessageNode
import me.rerere.rikkahub.desktop.data.SettingsStore
import me.rerere.rikkahub.desktop.llm.ChatParams
import me.rerere.rikkahub.desktop.llm.LlmClient
import me.rerere.rikkahub.desktop.llm.SearchClient
import me.rerere.rikkahub.desktop.llm.StreamDelta
import me.rerere.rikkahub.desktop.llm.ToolDefinition
import me.rerere.rikkahub.desktop.llm.ToolExchange
import me.rerere.rikkahub.desktop.llm.TtsClient
import me.rerere.rikkahub.desktop.llm.applyMessageTemplate
import me.rerere.rikkahub.desktop.llm.applyPromptVariables
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent

class ChatViewModel(
    val settingsStore: SettingsStore = SettingsStore(),
    val conversationStore: ConversationStore = ConversationStore(),
    private val searchClient: SearchClient = SearchClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _conversations = MutableStateFlow(conversationStore.list())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _current = MutableStateFlow(_conversations.value.firstOrNull())
    val current: StateFlow<Conversation?> = _current

    // 设置用 Compose 快照状态直接观察（不走 StateFlow，杜绝发射被吞的可能）
    var settings by mutableStateOf(settingsStore.settings.copy())
        private set

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    private val _streamingReasoning = MutableStateFlow("")
    val streamingReasoning: StateFlow<String> = _streamingReasoning

    /** 工具执行状态（如「正在写入记忆…」，null=无工具运行），显示在流式气泡处 */
    private val _toolRunning = MutableStateFlow<String?>(null)
    val toolRunning: StateFlow<String?> = _toolRunning

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 待发送的图片附件（data URL） */
    private val _pendingImages = MutableStateFlow<List<String>>(emptyList())
    val pendingImages: StateFlow<List<String>> = _pendingImages

    /** 待发送的文档附件（文件名 to 文本内容） */
    private val _pendingDocuments = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pendingDocuments: StateFlow<List<Pair<String, String>>> = _pendingDocuments

    fun addPendingImage(dataUrl: String) { _pendingImages.value = _pendingImages.value + dataUrl }
    fun removePendingImage(index: Int) {
        _pendingImages.value = _pendingImages.value.filterIndexed { i, _ -> i != index }
    }

    fun addPendingDocument(name: String, text: String) { _pendingDocuments.value = _pendingDocuments.value + (name to text) }
    fun removePendingDocument(index: Int) {
        _pendingDocuments.value = _pendingDocuments.value.filterIndexed { i, _ -> i != index }
    }

    private var streamJob: Job? = null

    /** 翻译结果（null=无弹窗） */
    private val _translation = MutableStateFlow<String?>(null)
    val translation: StateFlow<String?> = _translation

    private val _translationLoading = MutableStateFlow(false)
    val translationLoading: StateFlow<Boolean> = _translationLoading

    fun dismissTranslation() { _translation.value = null }

    /** 翻译消息（对齐安卓 translateMessage）：独立非流式调用，弹窗展示 */
    fun translateMessage(content: String) {
        if (_translationLoading.value) return
        val provider = settingsStore.activeProvider() ?: run { _error.value = "请先在设置中添加 Provider"; return }
        val model = settings.translateModelId ?: settings.activeModel ?: provider.models.firstOrNull()
            ?: run { _error.value = "未选择模型"; return }
        _translation.value = ""
        _translationLoading.value = true
        scope.launch {
            val result = LlmClient.of(provider).complete(provider, model, settings.translatePrompt + "\n\n" + content.take(4000))
            _translationLoading.value = false
            _translation.value = result ?: "翻译失败，请检查模型配置"
        }
    }

    // ===== 记忆 Memory =====
    private val memoryStore = MemoryStore()
    private val memoryJson = Json { ignoreUnknownKeys = true }

    /** 记忆归属 id：useGlobalMemory 时用固定全局 id（全助手共享），否则按助手隔离 */
    private fun memoryOwnerId(assistant: me.rerere.rikkahub.desktop.data.Assistant): String =
        if (assistant.useGlobalMemory) GLOBAL_MEMORY_ID else assistant.id

    /** memory 工具定义：模型自主读写长期记忆 */
    private val memoryTool = ToolDefinition(
        name = "memory",
        description = "读写长期记忆。action=create 新建记忆（需提供 content）；action=update 更新指定 id 的记忆（需提供 id 和 content）；action=delete 删除指定 id 的记忆（需提供 id）。",
        parametersSchema = """
            {
              "type": "object",
              "properties": {
                "action": {"type": "string", "enum": ["create", "update", "delete"], "description": "操作类型"},
                "id": {"type": "string", "description": "记忆 id，update/delete 时必填"},
                "content": {"type": "string", "description": "记忆内容，create/update 时必填"}
              },
              "required": ["action"]
            }
        """.trimIndent(),
    )

    /** systemPrompt 追加的记忆段：现有记忆 JSON + 工具使用规范（对齐安卓 buildMemoryPrompt 思路） */
    private fun buildMemoryPrompt(ownerId: String): String {
        val memJson = buildJsonArray {
            memoryStore.list(ownerId).forEach { m ->
                add(buildJsonObject {
                    put("id", m.id)
                    put("content", m.content)
                })
            }
        }
        return """

            # 长期记忆
            你拥有长期记忆能力，可以通过 memory 工具在对话中自主读写关于用户的重要信息。
            当前已保存的记忆（JSON）：
            $memJson
            使用规范：
            - 当用户透露重要偏好、个人信息、长期目标，或明确要求你记住/忘记某件事时，调用 memory 工具保存或删除对应记忆。
            - 相似或相关的内容应合并到同一条记忆（用 update 更新原条目），不要为同一主题重复新建多条。
            - 不要保存敏感信息（密码、密钥、证件号码等）。
            - 调用工具时保持静默，不要在回复中向用户赘述你执行了什么记忆操作。
        """.trimIndent()
    }

    /** 执行 memory 工具调用，返回给模型的简短结果文本 */
    private fun executeMemoryTool(ownerId: String?, call: StreamDelta.ToolCall): String {
        if (call.name != "memory") return "错误：未知工具 ${call.name}"
        if (ownerId == null) return "错误：记忆功能未开启"
        val args = runCatching { memoryJson.parseToJsonElement(call.argumentsJson).jsonObject }.getOrNull()
            ?: return "错误：参数不是合法 JSON"
        val action = args["action"]?.jsonPrimitive?.contentOrNull ?: return "错误：缺少 action"
        val id = args["id"]?.jsonPrimitive?.contentOrNull
        val content = args["content"]?.jsonPrimitive?.contentOrNull
        return when (action) {
            "create" ->
                if (content.isNullOrBlank()) "错误：create 需要提供 content"
                else { memoryStore.create(ownerId, content); "记忆已保存" }

            "update" -> when {
                id.isNullOrBlank() -> "错误：update 需要提供 id"
                content.isNullOrBlank() -> "错误：update 需要提供 content"
                memoryStore.update(ownerId, id, content) -> "记忆已更新"
                else -> "错误：未找到 id $id"
            }

            "delete" -> when {
                id.isNullOrBlank() -> "错误：delete 需要提供 id"
                memoryStore.delete(ownerId, id) -> "记忆已删除"
                else -> "错误：未找到 id $id"
            }

            else -> "错误：未知 action $action"
        }
    }

    // 设置页「管理记忆」对话框的桥接方法（按助手归属读写）
    fun listMemories(assistantId: String): List<MemoryEntry> {
        val a = settings.assistants.firstOrNull { it.id == assistantId } ?: return emptyList()
        return memoryStore.list(memoryOwnerId(a))
    }

    fun createMemory(assistantId: String, content: String) {
        val a = settings.assistants.firstOrNull { it.id == assistantId } ?: return
        memoryStore.create(memoryOwnerId(a), content)
    }

    fun updateMemory(assistantId: String, id: String, content: String): Boolean {
        val a = settings.assistants.firstOrNull { it.id == assistantId } ?: return false
        return memoryStore.update(memoryOwnerId(a), id, content)
    }

    fun deleteMemory(assistantId: String, id: String): Boolean {
        val a = settings.assistants.firstOrNull { it.id == assistantId } ?: return false
        return memoryStore.delete(memoryOwnerId(a), id)
    }

    // ===== 上下文压缩 =====
    private val _compressing = MutableStateFlow(false)
    val compressing: StateFlow<Boolean> = _compressing

    /** 一键压缩当前会话历史（对齐安卓压缩上下文）：用摘要替换全部消息节点 */
    fun compressConversation() {
        if (_streaming.value || _compressing.value) return
        val conv = _current.value ?: return
        val messages = conv.currentMessages
        if (messages.size < 4) { _error.value = "消息太少，无需压缩"; return }
        val provider = settingsStore.activeProvider() ?: run { _error.value = "请先在设置中添加 Provider"; return }
        val assistant = conv.assistantId?.let { id -> settings.assistants.firstOrNull { it.id == id } }
            ?: settings.activeAssistant()
        // 压缩模型：未指定时跟随聊天模型
        val model = settings.compressModelId ?: assistant?.chatModel ?: settings.activeModel
            ?: provider.models.firstOrNull() ?: run { _error.value = "未选择模型"; return }
        // 拼接对话历史（去掉图片，逐条截断 + 总量截断）
        val history = buildString {
            messages.forEach { m ->
                val role = if (m.role == "user") "用户" else "助手"
                append(role).append("：").append(m.content.take(1000)).append("\n\n")
            }
        }.take(12000)
        _compressing.value = true
        scope.launch {
            runCatching {
                val summary = LlmClient.of(provider).complete(provider, model, settings.compressPrompt + "\n\n" + history)
                if (summary.isNullOrBlank()) error("模型未返回摘要")
                // 用单个 user 摘要节点替换全部消息节点
                val latest = conversationStore.get(conv.id) ?: error("会话不存在")
                latest.messageNodes.clear()
                latest.messageNodes.add(
                    MessageNode(messages = mutableListOf(ChatMessage(role = "user", content = "【前情摘要】\n$summary")))
                )
                latest.chatSuggestions = emptyList()
                conversationStore.save(latest)
                _current.value = conversationStore.get(latest.id)
                _conversations.value = conversationStore.list()
            }.onFailure { _error.value = "压缩失败: ${it.message}" }
            _compressing.value = false
        }
    }

    // ===== 语音朗读 (TTS) =====
    private val ttsClient = TtsClient()

    /** 正在朗读的消息 id（null=无播放） */
    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId

    private var currentClip: Clip? = null

    /** 朗读消息（ttsEnabled 关闭时忽略；再次点击同一消息则停止） */
    fun speakMessage(messageId: String, content: String) {
        if (!settings.ttsEnabled) return
        if (_speakingMessageId.value == messageId) { stopSpeaking(); return }
        stopSpeaking()
        if (content.isBlank()) return
        val provider = settingsStore.activeProvider() ?: run { _error.value = "请先在设置中添加 Provider"; return }
        _speakingMessageId.value = messageId
        scope.launch {
            val bytes = ttsClient.speak(provider, settings.ttsModel, settings.ttsVoice, content.take(2000))
            if (bytes == null) {
                _speakingMessageId.value = null
                _error.value = "朗读失败: TTS 请求失败，请检查 TTS 模型与 Provider 配置"
                return@launch
            }
            runCatching {
                val clip = AudioSystem.getClip()
                clip.open(AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)))
                clip.addLineListener { e ->
                    // 播完（STOP）或手动停止/关闭时清空朗读状态
                    if (e.type == LineEvent.Type.STOP || e.type == LineEvent.Type.CLOSE) {
                        if (_speakingMessageId.value == messageId) _speakingMessageId.value = null
                    }
                }
                currentClip = clip
                clip.start()
            }.onFailure {
                _speakingMessageId.value = null
                _error.value = "朗读失败: ${it.message}"
            }
        }
    }

    /** 停止当前朗读 */
    fun stopSpeaking() {
        currentClip?.runCatching { stop(); close() }
        currentClip = null
        _speakingMessageId.value = null
    }

    /** 收藏/取消收藏消息（持久化在会话 JSON 里） */
    fun toggleFavorite(messageId: String) {
        val conv = _current.value ?: return
        conv.messageNodes.forEach { node ->
            val idx = node.messages.indexOfFirst { it.id == messageId }
            if (idx >= 0) {
                node.messages[idx] = node.messages[idx].copy(favorite = !node.messages[idx].favorite)
            }
        }
        conversationStore.save(conv)
        _current.value = conversationStore.get(conv.id)
    }

    /** 置顶/取消置顶会话 */
    fun togglePinConversation(id: String) {
        val conv = conversationStore.get(id) ?: return
        conv.pinned = !conv.pinned
        conversationStore.save(conv)
        _conversations.value = conversationStore.list()
        if (_current.value?.id == id) _current.value = conv
    }

    /** 手动重命名会话（标题非「新对话」后 AI 自动标题不再覆盖） */
    fun renameConversation(id: String, newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty()) return
        val conv = conversationStore.get(id) ?: return
        conv.title = title
        conversationStore.save(conv)
        _conversations.value = conversationStore.list()
        if (_current.value?.id == id) _current.value = conv
    }

    /** 把会话移动到其他助手（更新绑定的 assistantId） */
    fun moveConversationToAssistant(id: String, assistantId: String) {
        val conv = conversationStore.get(id) ?: return
        if (settings.assistants.none { it.id == assistantId }) return
        conv.assistantId = assistantId
        conversationStore.save(conv)
        _conversations.value = conversationStore.list()
        if (_current.value?.id == id) _current.value = conv
    }

    /** Fork 会话：以指定消息所在节点为止（含该节点）深拷贝出一个新会话并切换过去 */
    fun forkConversation(messageId: String) {
        val conv = _current.value ?: return
        val nodeIdx = conv.messageNodes.indexOfFirst { n -> n.messages.any { it.id == messageId } }
        if (nodeIdx < 0) return
        val nodesCopy = conv.messageNodes.take(nodeIdx + 1).map { node ->
            MessageNode(
                id = UUID.randomUUID().toString(),
                messages = node.messages.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList(),
                selectIndex = node.selectIndex,
            )
        }.toMutableList()
        if (nodesCopy.isEmpty()) return
        val fork = Conversation(
            // 「新对话」保持原标题，让 AI 自动标题仍能在新分支上生效
            title = if (conv.title == "新对话") conv.title else conv.title + "（分支）",
            messageNodes = nodesCopy,
            assistantId = conv.assistantId,
        )
        conversationStore.save(fork)
        _conversations.value = conversationStore.list()
        _current.value = fork
    }

    /** 导出会话为 Markdown 文本 */
    fun exportConversationMarkdown(id: String): String? {
        val conv = conversationStore.get(id) ?: return null
        val sb = StringBuilder("# ${conv.title}\n\n")
        conv.currentMessages.forEach { m ->
            val who = if (m.role == "user") "用户" else (m.model ?: "助手")
            sb.append("## ").append(who).append("\n\n")
            if (m.reasoning.isNotBlank()) {
                sb.append("> 思考：").append(m.reasoning.replace("\n", "\n> ")).append("\n\n")
            }
            sb.append(m.content).append("\n\n")
        }
        return sb.toString()
    }

    /** 全部收藏消息（跨会话，收藏夹视图用） */
    fun favoriteMessages(): List<Triple<Conversation, ChatMessage, MessageNode>> =
        _conversations.value.flatMap { conv ->
            conv.messageNodes.flatMap { node ->
                node.messages.filter { it.favorite }.map { Triple(conv, it, node) }
            }
        }.sortedByDescending { it.second.createdAt }

    fun refreshSettings() { settings = settingsStore.settings.copy() }

    /** 更新设置并刷新 UI；保存失败显示到错误条（不再静默吞异常） */
    fun updateSettings(block: AppSettings.() -> Unit) {
        runCatching { settingsStore.update(block) }
            .onSuccess { refreshSettings() }
            .onFailure {
                it.printStackTrace()
                _error.value = "设置保存失败: ${it.message}"
            }
    }

    fun newConversation() {
        val c = createWithPresetMessages(settings.activeAssistantId)
        _conversations.value = conversationStore.list()
        _current.value = c
    }

    /** 新建会话并写入助手预置消息（开场白），预置消息作为初始 messageNodes 参与后续上下文 */
    private fun createWithPresetMessages(assistantId: String?): Conversation {
        val c = conversationStore.create(assistantId = assistantId)
        val assistant = assistantId?.let { id -> settings.assistants.firstOrNull { it.id == id } }
            ?: settings.activeAssistant()
        val presets = assistant?.presetMessages?.filter { it.content.isNotBlank() } ?: return c
        presets.forEach { pm ->
            c.messageNodes.add(
                MessageNode(
                    messages = mutableListOf(
                        ChatMessage(role = if (pm.role == "assistant") "assistant" else "user", content = pm.content)
                    )
                )
            )
        }
        return conversationStore.save(c)
    }

    /** 应用助手正则变换（对齐安卓 AssistantRegex 子集：input/output 作用域） */
    private fun applyRegex(text: String, assistant: me.rerere.rikkahub.desktop.data.Assistant?, scope: String): String {
        if (text.isEmpty() || assistant == null) return text
        var result = text
        assistant.regexes.filter { it.enabled && it.scope == scope && it.find.isNotBlank() }.forEach { r ->
            result = runCatching { result.replace(Regex(r.find), r.replace) }.getOrElse { result }
        }
        return result
    }

    /** 切换当前助手（影响之后新建的会话） */
    fun selectAssistant(id: String) {
        updateSettings { activeAssistantId = id }
    }

    fun selectConversation(id: String) {
        _current.value = conversationStore.get(id)
    }

    fun deleteConversation(id: String) {
        conversationStore.delete(id)
        _conversations.value = conversationStore.list()
        if (_current.value?.id == id) _current.value = _conversations.value.firstOrNull()
    }

    /** 切换全局聊天模型（输入栏模型下拉），同时清掉当前助手的模型覆盖使选择立即生效 */
    fun switchModel(model: String) {
        val assistantId = _current.value?.assistantId ?: settings.activeAssistantId
        updateSettings {
            activeModel = model
            val idx = assistants.indexOfFirst { it.id == assistantId }
            if (idx >= 0 && assistants[idx].chatModel != null) {
                assistants[idx] = assistants[idx].copy(chatModel = null)
            }
        }
    }

    /** 设置当前助手的推理力度（输入栏推理下拉） */
    fun setAssistantReasoningEffort(effort: String?) {
        val id = _current.value?.assistantId ?: settings.activeAssistantId ?: return
        updateSettings {
            val idx = assistants.indexOfFirst { it.id == id }
            if (idx >= 0) assistants[idx] = assistants[idx].copy(reasoningEffort = effort)
        }
    }

    fun send(text: String) {
        if (_streaming.value) return
        val images = _pendingImages.value
        val docs = _pendingDocuments.value
        if (text.isBlank() && images.isEmpty() && docs.isEmpty()) return
        if (settingsStore.activeProvider() == null) { _error.value = "请先在设置中添加 Provider"; return }
        if (settings.activeModel == null &&
            settingsStore.activeProvider()?.models.isNullOrEmpty()
        ) { _error.value = "未选择模型"; return }

        // 文档附件转为提示词前缀（对齐 Android DocumentAsPromptTransformer 的思路）
        val fullText = buildString {
            docs.forEach { (name, content) ->
                append("【文件：$name】\n```\n").append(content).append("\n```\n\n")
            }
            append(text)
        }.trim()

        var conv = _current.value
            ?: createWithPresetMessages(settings.activeAssistantId)
        conv.messageNodes.add(
            MessageNode(
                messages = mutableListOf(
                    ChatMessage(role = "user", content = fullText, imageUrls = images)
                )
            )
        )
        // 标题留给生成完成后的 AI 标题（失败时回退为首条消息截断）
        conv.chatSuggestions = emptyList()
        _pendingImages.value = emptyList()
        _pendingDocuments.value = emptyList()
        conv = conversationStore.save(conv)
        _current.value = conv
        _conversations.value = conversationStore.list()
        _error.value = null
        startGeneration(conv)
    }

    /** 重新生成：在最后一条 assistant 节点上新建一个分支变体 */
    fun regenerate() {
        if (_streaming.value) return
        val conv = _current.value ?: return
        val lastNode = conv.messageNodes.lastOrNull() ?: return
        if (lastNode.currentMessage?.role != "assistant") return
        startGeneration(conv)
    }

    private fun startGeneration(conv: Conversation) {
        val provider = settingsStore.activeProvider()
        if (provider == null) { _error.value = "请先在设置中添加 Provider"; return }
        val s = settings
        // 会话绑定的助手优先，其次当前选中的助手
        val assistant = conv.assistantId?.let { id -> s.assistants.firstOrNull { it.id == id } }
            ?: s.activeAssistant()
        val model = assistant?.chatModel ?: s.activeModel ?: provider.models.firstOrNull()
        if (model == null) { _error.value = "未选择模型"; return }
        // 提示词变量替换（对齐安卓 PlaceholderTransformer，作用于 systemPrompt）
        val baseSystemPrompt = applyPromptVariables(
            assistant?.systemPrompt?.ifBlank { s.systemPrompt } ?: s.systemPrompt,
            model = model,
            modelDisplayName = model,
            assistantName = assistant?.name,
            userNickname = s.userNickname,
        )
        // 记忆：开启时注入记忆段并注册 memory 工具
        val memoryOwner = assistant?.takeIf { it.enableMemory }?.let { memoryOwnerId(it) }
        val systemPrompt = baseSystemPrompt + (memoryOwner?.let { buildMemoryPrompt(it) } ?: "")
        val tools = if (memoryOwner != null) listOf(memoryTool) else emptyList()
        val temperature = assistant?.temperature ?: s.temperature
        val reasoningEffort = assistant?.reasoningEffort
        val topP = assistant?.topP
        val maxTokens = assistant?.maxTokens
        val contextSize = assistant?.contextMessageSize ?: 40

        // 追加空的 assistant 变体：最后一个节点不是 assistant 时新建节点，否则新建分支
        val lastNode = conv.messageNodes.lastOrNull()
        if (lastNode == null || lastNode.currentMessage?.role != "assistant") {
            conv.messageNodes.add(
                MessageNode(messages = mutableListOf(ChatMessage(role = "assistant", content = "")))
            )
        } else {
            lastNode.messages.add(ChatMessage(role = "assistant", content = ""))
            lastNode.selectIndex = lastNode.messages.size - 1
        }
        conversationStore.save(conv)
        _current.value = conv

        // 输入正则：作用于发送给 API 的用户消息（不改动本地存储）
        val rawContext = conv.currentMessages.dropLast(1)
        val messageTemplate = assistant?.messageTemplate.orEmpty()
        val context = rawContext.map { msg ->
            var m = msg
            if (m.role == "user") m = m.copy(content = applyRegex(m.content, assistant, "input"))
            // system 消息同样应用提示词变量（用户消息不替换）
            if (m.role == "system") {
                m = m.copy(content = applyPromptVariables(m.content, model, model, assistant?.name, s.userNickname))
            }
            // 消息模板：串在正则之后，只作用于发送内容，不改本地存储
            if (messageTemplate.isNotBlank() && m.content.isNotBlank()) {
                m = m.copy(content = applyMessageTemplate(messageTemplate, m.content, m.role, m.createdAt))
            }
            m
        }
        val targetNode = conv.messageNodes.last()
        val targetIndex = targetNode.selectIndex

        _streaming.value = true
        _streamingText.value = ""
        _streamingReasoning.value = ""
        _toolRunning.value = null
        streamJob = scope.launch {
            // 联网搜索：用最后一条用户消息做查询，结果注入 system 上下文
            var searchContext: String? = null
            if (settings.searchEnabled && settings.searchApiKey.isNotBlank()) {
                val query = context.lastOrNull { it.role == "user" }?.content?.take(300)
                if (!query.isNullOrBlank()) {
                    searchContext = searchClient.search(settings.searchService, settings.searchApiKey, query, settings.searchResultSize)
                }
            }
            val sb = StringBuilder()
            val rsb = StringBuilder()
            var promptTokens: Int? = null
            var completionTokens: Int? = null
            val startAt = System.currentTimeMillis()
            var firstReasoningAt = 0L
            var firstContentAt = 0L
            // 已完成的 工具调用→结果 序列（工具循环每轮请求续传）
            val exchanges = mutableListOf<ToolExchange>()
            try {
                // 工具循环：模型发起工具调用则执行后续传，直到输出正文或达到 8 步上限（防死循环）
                var step = 0
                while (true) {
                    var gotToolCall = false
                    var failed = false
                    runCatching {
                        LlmClient.of(provider).streamChat(
                            provider,
                            ChatParams(
                                model = model,
                                history = context,
                                systemPrompt = systemPrompt,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokens,
                                contextSize = contextSize,
                                reasoningEffort = reasoningEffort,
                                searchContext = searchContext,
                                tools = tools,
                                toolExchanges = exchanges.toList(),
                                customHeaders = assistant?.customHeaders ?: emptyList(),
                                customBodies = assistant?.customBodies ?: emptyList(),
                            ),
                        ).collect { delta ->
                            when (delta) {
                                is StreamDelta.Content -> {
                                    if (firstContentAt == 0L) firstContentAt = System.currentTimeMillis()
                                    _toolRunning.value = null
                                    sb.append(delta.text)
                                    _streamingText.value = sb.toString()
                                }
                                is StreamDelta.Reasoning -> {
                                    if (firstReasoningAt == 0L) firstReasoningAt = System.currentTimeMillis()
                                    rsb.append(delta.text)
                                    _streamingReasoning.value = rsb.toString()
                                }
                                is StreamDelta.Usage -> {
                                    promptTokens = delta.promptTokens
                                    completionTokens = delta.completionTokens
                                }
                                is StreamDelta.ToolCall -> {
                                    gotToolCall = true
                                    _toolRunning.value = "正在写入记忆…"
                                    val result = executeMemoryTool(memoryOwner, delta)
                                    exchanges.add(ToolExchange(delta.id, delta.name, delta.argumentsJson, result))
                                }
                            }
                        }
                    }.onFailure { e ->
                        // 取消（停止生成）必须向外抛，保证 finally 保存已生成内容后协程正常结束
                        if (e is CancellationException) throw e
                        _error.value = e.message
                        failed = true
                    }
                    step++
                    if (failed || !gotToolCall || step >= 8) break
                }
            } finally {
                _toolRunning.value = null
                // 停止/出错时保留已生成的部分内容；输出正则作用于最终回复
                targetNode.messages[targetIndex] = targetNode.messages[targetIndex].copy(
                    content = applyRegex(sb.toString(), assistant, "output"),
                    reasoning = rsb.toString(),
                    model = model,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    generationMs = System.currentTimeMillis() - startAt,
                    reasoningMs = if (firstReasoningAt > 0) {
                        (if (firstContentAt > 0) firstContentAt else System.currentTimeMillis()) - firstReasoningAt
                    } else null,
                )
                conversationStore.save(conv)
                _current.value = conversationStore.get(conv.id)
                _conversations.value = conversationStore.list()
                _streaming.value = false
                if (sb.isNotBlank()) {
                    generateTitle(conv.id, provider, settings.titleModelId ?: model, context)
                    generateSuggestions(conv.id, provider, settings.suggestionModelId ?: model, context, sb.toString())
                }
            }
        }
    }

    /** AI 生成会话标题（对齐安卓 generateTitle），失败回退为首条消息截断 */
    private fun generateTitle(
        conversationId: String,
        provider: me.rerere.rikkahub.desktop.data.ProviderConfig,
        model: String,
        context: List<ChatMessage>,
    ) {
        scope.launch {
            val conv = conversationStore.get(conversationId) ?: return@launch
            if (conv.title != "新对话") return@launch
            val firstUser = context.firstOrNull { it.role == "user" }?.content?.take(300) ?: return@launch
            val title = LlmClient.of(provider).complete(provider, model, settings.titlePrompt + "\n\n" + firstUser)
                ?.lines()?.firstOrNull()?.trim()?.take(30)
                ?: firstUser.lines().first().take(20)
            if (title.isBlank()) return@launch
            val latest = conversationStore.get(conversationId) ?: return@launch
            if (latest.title != "新对话") return@launch
            latest.title = title
            conversationStore.save(latest)
            _conversations.value = conversationStore.list()
            if (_current.value?.id == conversationId) {
                _current.value = latest
            }
        }
    }

    /** 生成对话建议（对齐 Android generateSuggestion）：一次非流式短调用 */
    private fun generateSuggestions(
        conversationId: String,
        provider: me.rerere.rikkahub.desktop.data.ProviderConfig,
        model: String,
        context: List<ChatMessage>,
        reply: String,
    ) {
        scope.launch {
            val lastUser = context.lastOrNull { it.role == "user" }?.content?.take(500) ?: return@launch
            val prompt = buildString {
                append(settings.suggestionPrompt).append("\n\n")
                append("用户：").append(lastUser).append('\n')
                append("助手：").append(reply.take(800))
            }
            val raw = LlmClient.of(provider).complete(provider, model, prompt) ?: return@launch
            val suggestions = raw.lines()
                .map { it.trim().replace(Regex("""^[-*•\d.、)\s]+"""), "") }
                .filter { it.isNotBlank() }
                .take(4)
            if (suggestions.isEmpty()) return@launch
            val latest = conversationStore.get(conversationId) ?: return@launch
            latest.chatSuggestions = suggestions
            conversationStore.save(latest)
            if (_current.value?.id == conversationId) {
                _current.value = latest
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        _streaming.value = false
    }

    /** 切换节点的分支 */
    fun selectBranch(nodeId: String, index: Int) {
        val conv = _current.value ?: return
        val node = conv.messageNodes.find { it.id == nodeId } ?: return
        if (index !in node.messages.indices) return
        node.selectIndex = index
        conversationStore.save(conv)
        _current.value = conversationStore.get(conv.id)
    }

    /** 删除单条消息（分支变体）；节点空了就移除节点 */
    fun deleteMessage(messageId: String) {
        val conv = _current.value ?: return
        val nodeIdx = conv.messageNodes.indexOfFirst { n -> n.messages.any { it.id == messageId } }
        if (nodeIdx < 0) return
        val node = conv.messageNodes[nodeIdx]
        node.messages.removeAll { it.id == messageId }
        if (node.messages.isEmpty()) {
            conv.messageNodes.removeAt(nodeIdx)
        } else {
            node.selectIndex = node.selectIndex.coerceIn(0, node.messages.size - 1)
        }
        conversationStore.save(conv)
        _current.value = conversationStore.get(conv.id)
    }

    /** 编辑消息并重新发送：产生新分支变体，并截断其后所有节点 */
    fun editAndResend(messageId: String, newContent: String) {
        if (_streaming.value || newContent.isBlank()) return
        val conv = _current.value ?: return
        val nodeIdx = conv.messageNodes.indexOfFirst { n -> n.messages.any { it.id == messageId } }
        if (nodeIdx < 0) return
        val node = conv.messageNodes[nodeIdx]
        val msg = node.messages.find { it.id == messageId } ?: return
        node.messages.add(msg.copy(id = UUID.randomUUID().toString(), content = newContent))
        node.selectIndex = node.messages.size - 1
        while (conv.messageNodes.size > nodeIdx + 1) {
            conv.messageNodes.removeAt(conv.messageNodes.size - 1)
        }
        conversationStore.save(conv)
        _current.value = conv
        if (msg.role == "user") startGeneration(conv)
    }

    /** 直接用 baseUrl/apiKey 拉模型（设置页添加新 Provider 时） */
    fun fetchModelsRaw(baseUrl: String, apiKey: String, type: String = "openai", onResult: (List<String>) -> Unit) {
        _modelsLoading.value = true
        scope.launch {
            val temp = me.rerere.rikkahub.desktop.data.ProviderConfig(
                name = "temp", baseUrl = baseUrl, apiKey = apiKey, type = type
            )
            val models = LlmClient.of(temp).listModels(temp)
            _modelsLoading.value = false
            onResult(models)
        }
    }

    /** 从 Provider 拉取模型列表 */
    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading

    fun fetchModels(providerId: String, onResult: (List<String>) -> Unit) {
        val provider = settings.providers.firstOrNull { it.id == providerId } ?: return
        _modelsLoading.value = true
        scope.launch {
            val models = LlmClient.of(provider).listModels(provider)
            _modelsLoading.value = false
            onResult(models)
        }
    }

    fun clearError() { _error.value = null }

    // ===== 本地备份/恢复 =====

    /** 导出备份到指定 zip 路径（后台线程执行） */
    fun exportBackupTo(path: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            runCatching { Backup.exportBackup(java.io.File(path)) }
                .onSuccess { onResult(true, "备份已导出") }
                .onFailure { onResult(false, "导出失败: ${it.message}") }
        }
    }

    /** 从 zip 恢复备份（后台线程执行）；成功后重载设置并刷新会话列表 */
    fun importBackupFrom(path: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            runCatching { Backup.importBackup(java.io.File(path)) }
                .onSuccess {
                    settingsStore.reload()
                    refreshSettings()
                    _conversations.value = conversationStore.list()
                    _current.value = _conversations.value.firstOrNull { it.id == _current.value?.id }
                        ?: _conversations.value.firstOrNull()
                    onResult(true, "备份已恢复")
                }
                .onFailure { onResult(false, "恢复失败: ${it.message}") }
        }
    }
}
