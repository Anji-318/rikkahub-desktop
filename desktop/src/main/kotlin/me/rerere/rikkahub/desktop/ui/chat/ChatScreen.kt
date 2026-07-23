package me.rerere.rikkahub.desktop.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch
import me.rerere.rikkahub.desktop.data.ChatMessage
import me.rerere.rikkahub.desktop.data.Conversation
import me.rerere.rikkahub.desktop.data.MessageNode

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val conversations by vm.conversations.collectAsState()
    val current by vm.current.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val streamingReasoning by vm.streamingReasoning.collectAsState()
    val error by vm.error.collectAsState()
    val settings by vm.settings.collectAsState()

    Row(Modifier.fillMaxSize()) {
        // ===== 左侧会话栏 =====
        Column(
            Modifier.width(250.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
        ) {
            Button(
                onClick = { vm.newConversation() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建对话", fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            // 助手选择（影响之后新建的会话）
            var assistantMenu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { assistantMenu = true }
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            settings.assistants.firstOrNull { it.id == settings.activeAssistantId }?.name ?: "默认助手",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(expanded = assistantMenu, onDismissRequest = { assistantMenu = false }) {
                    settings.assistants.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.name, fontSize = 13.sp) },
                            onClick = { vm.selectAssistant(a.id); assistantMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { c ->
                    ConversationItem(
                        c = c,
                        selected = c.id == current?.id,
                        onClick = { vm.selectConversation(c.id) },
                        onDelete = { vm.deleteConversation(c.id) },
                    )
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onOpenSettings() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("设置", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ===== 右侧聊天区 =====
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(current?.title ?: "RikkaHub", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    settings.activeModel?.let {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(99.dp)) {
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            HorizontalDivider()

            // 消息列表
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val msgCount = (current?.messageNodes?.size ?: 0) + if (streaming) 1 else 0
            LaunchedEffect(msgCount, streamingText.length) {
                if (msgCount > 0) scope.launch { listState.animateScrollToItem(msgCount - 1) }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                current?.messageNodes?.let { nodes ->
                    // 流式生成时，最后一条空 assistant 占位消息由流式气泡渲染，避免重复
                    val displayNodes = if (streaming &&
                        nodes.lastOrNull()?.currentMessage?.role == "assistant" &&
                        nodes.lastOrNull()?.currentMessage?.content.isNullOrEmpty()
                    ) nodes.dropLast(1) else nodes
                    items(displayNodes, key = { it.id }) { node ->
                        val m = node.currentMessage ?: return@items
                        MessageBubble(
                            message = m,
                            node = node,
                            streaming = false,
                            isLast = node.id == displayNodes.lastOrNull()?.id && !streaming,
                            onDelete = { vm.deleteMessage(m.id) },
                            onRegenerate = { vm.regenerate() },
                            onEditResend = { newText -> vm.editAndResend(m.id, newText) },
                            onSelectBranch = { idx -> vm.selectBranch(node.id, idx) },
                        )
                    }
                }
                if (streaming) {
                    item {
                        MessageBubble(
                            message = ChatMessage(
                                role = "assistant",
                                content = streamingText.ifEmpty { "▍" },
                                reasoning = streamingReasoning,
                            ),
                            streaming = true,
                            isLast = true,
                            onDelete = {}, onRegenerate = {}, onEditResend = {},
                        )
                    }
                }
            }

            // 错误提示
            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearError() }) { Text("关闭", fontSize = 12.sp) }
                    }
                }
            }

            // 输入栏（Enter 发送 / Shift+Enter 换行）
            var input by remember { mutableStateOf("") }
            fun doSend() {
                if (input.isNotBlank() && !streaming) {
                    vm.send(input)
                    input = ""
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isCtrlPressed &&
                                !(e.nativeKeyEvent as? java.awt.event.KeyEvent)?.isShiftDown!!) {
                                doSend(); true
                            } else false
                        },
                        placeholder = { Text("输入消息…（Enter 发送，Shift+Enter 换行）", fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 6,
                    )
                    Spacer(Modifier.width(10.dp))
                    if (streaming) {
                        FilledIconButton(onClick = { vm.stop() }) { Icon(Icons.Default.Stop, "停止") }
                    } else {
                        FilledIconButton(onClick = { doSend() }, enabled = input.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "发送")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(c: Conversation, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp).clickable { onDelete() }, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    streaming: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: (String) -> Unit,
    node: MessageNode? = null,
    onSelectBranch: (Int) -> Unit = {},
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }

    Column(
        Modifier.fillMaxWidth().hoverable(interactionSource),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 14.dp, topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 680.dp)
        ) {
            if (editing) {
                Column(Modifier.padding(10.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.widthIn(min = 320.dp),
                        maxLines = 8
                    )
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = false; onEditResend(editText) }) { Text("保存并重发", fontSize = 12.sp) }
                        TextButton(onClick = { editing = false; editText = message.content }) { Text("取消", fontSize = 12.sp) }
                    }
                }
            } else if (isUser) {
                Text(
                    message.content,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            } else {
                // AI 回复：思考链（可折叠）+ Markdown 渲染（代码块/加粗/列表/表格）
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (message.reasoning.isNotBlank()) {
                        var showThinking by remember { mutableStateOf(false) }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable { showThinking = !showThinking }
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "思考过程",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (showThinking) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        null,
                                        Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (showThinking) {
                                    Text(
                                        message.reasoning,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Markdown(
                        content = message.content,
                        colors = markdownColor(text = MaterialTheme.colorScheme.onSurface),
                        typography = markdownTypography(
                            text = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp)
                        ),
                    )
                }
            }
        }

        // token 用量
        if (!isUser && (message.promptTokens != null || message.completionTokens != null)) {
            Text(
                "↑ ${message.promptTokens ?: 0} tokens · ↓ ${message.completionTokens ?: 0} tokens",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 分支切换（节点有多条候选消息时常驻显示）
        if (node != null && node.messages.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                IconButton(
                    onClick = { onSelectBranch(node.selectIndex - 1) },
                    enabled = node.selectIndex > 0,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, "上一分支", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${node.selectIndex + 1}/${node.messages.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onSelectBranch(node.selectIndex + 1) },
                    enabled = node.selectIndex < node.messages.size - 1,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, "下一分支", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 悬停显示操作栏
        if ((hovered && !streaming) || editing) {
            Row(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ContentCopy, "复制", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isUser) {
                    IconButton(onClick = { editing = true }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, "编辑", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!isUser && isLast) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Refresh, "重新生成", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}
