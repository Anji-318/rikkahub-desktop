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
import me.rerere.rikkahub.desktop.data.AppSettings
import me.rerere.rikkahub.desktop.data.ChatMessage
import me.rerere.rikkahub.desktop.data.Conversation
import me.rerere.rikkahub.desktop.data.ConversationStore
import me.rerere.rikkahub.desktop.data.MessageNode
import me.rerere.rikkahub.desktop.data.SettingsStore
import me.rerere.rikkahub.desktop.llm.OpenAiClient
import me.rerere.rikkahub.desktop.llm.StreamDelta
import java.util.UUID

class ChatViewModel(
    val settingsStore: SettingsStore = SettingsStore(),
    val conversationStore: ConversationStore = ConversationStore(),
    private val llm: OpenAiClient = OpenAiClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _conversations = MutableStateFlow(conversationStore.list())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _current = MutableStateFlow(_conversations.value.firstOrNull())
    val current: StateFlow<Conversation?> = _current

    private val _settings = MutableStateFlow(settingsStore.settings)
    val settings: StateFlow<AppSettings> = _settings

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

    fun addPendingImage(dataUrl: String) { _pendingImages.value = _pendingImages.value + dataUrl }
    fun removePendingImage(index: Int) {
        _pendingImages.value = _pendingImages.value.filterIndexed { i, _ -> i != index }
    }

    private var streamJob: Job? = null

    fun refreshSettings() { _settings.value = settingsStore.settings }

    fun newConversation() {
        val c = conversationStore.create(assistantId = _settings.value.activeAssistantId)
        _conversations.value = conversationStore.list()
        _current.value = c
    }

    /** 切换当前助手（影响之后新建的会话） */
    fun selectAssistant(id: String) {
        settingsStore.update { activeAssistantId = id }
        refreshSettings()
    }

    fun selectConversation(id: String) {
        _current.value = conversationStore.get(id)
    }

    fun deleteConversation(id: String) {
        conversationStore.delete(id)
        _conversations.value = conversationStore.list()
        if (_current.value?.id == id) _current.value = _conversations.value.firstOrNull()
    }

    /** 切换全局聊天模型（输入栏模型下拉） */
    fun switchModel(model: String) {
        settingsStore.update { activeModel = model }
        refreshSettings()
    }

    /** 设置当前助手的推理力度（输入栏推理下拉） */
    fun setAssistantReasoningEffort(effort: String?) {
        val id = _current.value?.assistantId ?: _settings.value.activeAssistantId ?: return
        settingsStore.update {
            val idx = assistants.indexOfFirst { it.id == id }
            if (idx >= 0) assistants[idx] = assistants[idx].copy(reasoningEffort = effort)
        }
        refreshSettings()
    }

    fun send(text: String) {
        if (_streaming.value) return
        val images = _pendingImages.value
        if (text.isBlank() && images.isEmpty()) return
        if (settingsStore.activeProvider() == null) { _error.value = "请先在设置中添加 Provider"; return }
        if (_settings.value.activeModel == null &&
            settingsStore.activeProvider()?.models.isNullOrEmpty()
        ) { _error.value = "未选择模型"; return }

        var conv = _current.value
            ?: conversationStore.create(assistantId = _settings.value.activeAssistantId)
        conv.messageNodes.add(
            MessageNode(
                messages = mutableListOf(
                    ChatMessage(role = "user", content = text, imageUrls = images)
                )
            )
        )
        if (conv.title == "新对话") conv.title = text.take(20).ifBlank { "图片对话" }
        _pendingImages.value = emptyList()
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
        val s = _settings.value
        // 会话绑定的助手优先，其次当前选中的助手
        val assistant = conv.assistantId?.let { id -> s.assistants.firstOrNull { it.id == id } }
            ?: s.activeAssistant()
        val model = assistant?.chatModel ?: s.activeModel ?: provider.models.firstOrNull()
        if (model == null) { _error.value = "未选择模型"; return }
        val systemPrompt = assistant?.systemPrompt?.ifBlank { s.systemPrompt } ?: s.systemPrompt
        val temperature = assistant?.temperature ?: s.temperature
        val reasoningEffort = assistant?.reasoningEffort

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
            val sb = StringBuilder()
            val rsb = StringBuilder()
            var promptTokens: Int? = null
            var completionTokens: Int? = null
            val startAt = System.currentTimeMillis()
            var firstReasoningAt = 0L
            var firstContentAt = 0L
            llm.streamChat(provider, model, context, systemPrompt, temperature, reasoningEffort)
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
        val provider = _settings.value.providers.firstOrNull { it.id == providerId } ?: return
        _modelsLoading.value = true
        scope.launch {
            val models = llm.listModels(provider)
            _modelsLoading.value = false
            onResult(models)
        }
    }

    fun clearError() { _error.value = null }
}
