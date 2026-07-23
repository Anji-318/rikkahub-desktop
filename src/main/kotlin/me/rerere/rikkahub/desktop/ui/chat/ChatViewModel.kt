package me.rerere.rikkahub.desktop.ui.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.desktop.data.AppSettings
import me.rerere.rikkahub.desktop.data.ChatMessage
import me.rerere.rikkahub.desktop.data.Conversation
import me.rerere.rikkahub.desktop.data.ConversationStore
import me.rerere.rikkahub.desktop.data.MessageNode
import me.rerere.rikkahub.desktop.data.SettingsStore
import me.rerere.rikkahub.desktop.llm.OpenAiClient
import me.rerere.rikkahub.desktop.llm.SearchClient
import me.rerere.rikkahub.desktop.llm.StreamDelta
import java.util.UUID

class ChatViewModel(
    val settingsStore: SettingsStore = SettingsStore(),
    val conversationStore: ConversationStore = ConversationStore(),
    private val llm: OpenAiClient = OpenAiClient(),
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
            val result = llm.complete(provider, model, settings.translatePrompt + "\n\n" + content.take(4000))
            _translationLoading.value = false
            _translation.value = result ?: "翻译失败，请检查模型配置"
        }
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
        val c = conversationStore.create(assistantId = settings.activeAssistantId)
        _conversations.value = conversationStore.list()
        _current.value = c
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
            ?: conversationStore.create(assistantId = settings.activeAssistantId)
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
        val systemPrompt = assistant?.systemPrompt?.ifBlank { s.systemPrompt } ?: s.systemPrompt
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

        val context = conv.currentMessages.dropLast(1)
        val targetNode = conv.messageNodes.last()
        val targetIndex = targetNode.selectIndex

        _streaming.value = true
        _streamingText.value = ""
        _streamingReasoning.value = ""
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
            llm.streamChat(
                provider, model, context, systemPrompt, temperature, reasoningEffort,
                topP, maxTokens, contextSize, searchContext,
            )
                .catch { e -> _error.value = e.message }
                .onCompletion {
                    // 停止/出错时保留已生成的部分内容
                    targetNode.messages[targetIndex] = targetNode.messages[targetIndex].copy(
                        content = sb.toString(),
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
                .collect { delta ->
                    when (delta) {
                        is StreamDelta.Content -> {
                            if (firstContentAt == 0L) firstContentAt = System.currentTimeMillis()
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
            val title = llm.complete(provider, model, settings.titlePrompt + "\n\n" + firstUser)
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
            val raw = llm.complete(provider, model, prompt) ?: return@launch
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
    fun fetchModelsRaw(baseUrl: String, apiKey: String, onResult: (List<String>) -> Unit) {
        _modelsLoading.value = true
        scope.launch {
            val models = llm.listModels(
                me.rerere.rikkahub.desktop.data.ProviderConfig(
                    name = "temp", baseUrl = baseUrl, apiKey = apiKey
                )
            )
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
            val models = llm.listModels(provider)
            _modelsLoading.value = false
            onResult(models)
        }
    }

    fun clearError() { _error.value = null }
}
