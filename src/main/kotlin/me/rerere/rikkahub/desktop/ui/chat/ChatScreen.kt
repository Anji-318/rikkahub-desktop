package me.rerere.rikkahub.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
import me.rerere.rikkahub.desktop.data.DisplaySetting
import me.rerere.rikkahub.desktop.data.MessageNode
import me.rerere.rikkahub.desktop.data.REASONING_EFFORTS
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val conversations by vm.conversations.collectAsState()
    val current by vm.current.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val streamingText by vm.streamingText.collectAsState()
    val streamingReasoning by vm.streamingReasoning.collectAsState()
    val error by vm.error.collectAsState()
    val settings = vm.settings
    val pendingImages by vm.pendingImages.collectAsState()
    val pendingDocuments by vm.pendingDocuments.collectAsState()
    val translation by vm.translation.collectAsState()
    val translationLoading by vm.translationLoading.collectAsState()
    var showFavorites by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        // ===== 左侧会话栏 =====
        Column(
            Modifier.width(250.dp).fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
        ) {
            // 用户信息（点击编辑昵称/头像）
            var showProfileEditor by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { showProfileEditor = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(settings.userNickname, settings.userAvatar, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(settings.userNickname, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(greeting(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showProfileEditor) {
                ProfileEditDialog(vm = vm, onDismiss = { showProfileEditor = false })
            }
            Spacer(Modifier.height(10.dp))

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

            // 搜索对话
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索对话", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Spacer(Modifier.height(10.dp))
            Text("会话", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))

            val filtered = remember(conversations, query) {
                if (query.isBlank()) conversations
                else conversations.filter { c ->
                    c.title.contains(query, true) ||
                        c.currentMessages.any { it.content.contains(query, true) }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (query.isNotBlank()) {
                    items(filtered, key = { it.id }) { c ->
                        ConversationItem(
                            c = c,
                            selected = c.id == current?.id,
                            onClick = { vm.selectConversation(c.id) },
                            onDelete = { vm.deleteConversation(c.id) },
                        )
                    }
                } else {
                    val groups = filtered.groupBy { dayLabel(it.updatedAt) }
                    groups.forEach { (label, list) ->
                        item(key = "header-$label") {
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(list, key = { it.id }) { c ->
                            ConversationItem(
                                c = c,
                                selected = c.id == current?.id,
                                onClick = { vm.selectConversation(c.id) },
                                onDelete = { vm.deleteConversation(c.id) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
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
                            trailingIcon = {
                                if (a.id == settings.activeAssistantId) {
                                    Icon(Icons.Default.Check, "当前", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = { vm.selectAssistant(a.id); assistantMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onOpenSettings() }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("设置", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().clickable { showFavorites = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("收藏夹", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ===== 右侧聊天区 =====
        Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
            // 顶栏：标题 + 助手/模型/Provider
            val assistant = current?.assistantId?.let { id -> settings.assistants.firstOrNull { it.id == id } }
                ?: settings.activeAssistant()
            val provider = settings.providers.firstOrNull { it.id == settings.activeProviderId }
            val modelName = assistant?.chatModel ?: settings.activeModel
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        current?.title ?: "RikkaHub",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(assistant?.name ?: "默认助手")
                            modelName?.let { append(" / $it") }
                            provider?.let { append(" (${it.name})") }
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        MessageRow(
                            message = m,
                            node = node,
                            streaming = false,
                            isLast = node.id == displayNodes.lastOrNull()?.id && !streaming,
                            userNickname = settings.userNickname,
                            userAvatar = settings.userAvatar,
                            ds = settings.displaySetting,
                            onDelete = { vm.deleteMessage(m.id) },
                            onRegenerate = { vm.regenerate() },
                            onEditResend = { newText -> vm.editAndResend(m.id, newText) },
                            onSelectBranch = { idx -> vm.selectBranch(node.id, idx) },
                            onTranslate = { vm.translateMessage(m.content) },
                            onToggleFavorite = { vm.toggleFavorite(m.id) },
                        )
                    }
                }
                if (streaming) {
                    item {
                        MessageRow(
                            message = ChatMessage(
                                role = "assistant",
                                content = streamingText,
                                reasoning = streamingReasoning,
                                model = modelName,
                            ),
                            node = null,
                            streaming = true,
                            isLast = true,
                            ds = settings.displaySetting,
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

            // ===== 输入栏 =====
            var input by remember { mutableStateOf("") }
            fun doSend() {
                if ((input.isNotBlank() || pendingImages.isNotEmpty()) && !streaming) {
                    vm.send(input)
                    input = ""
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    // 待发送图片
                    if (pendingImages.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            pendingImages.forEachIndexed { idx, dataUrl ->
                                Box {
                                    DataUrlImage(
                                        dataUrl = dataUrl,
                                        modifier = Modifier.size(48.dp).background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        "移除",
                                        modifier = Modifier.align(Alignment.TopEnd).size(16.dp)
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            .clickable { vm.removePendingImage(idx) },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    // 待发送文档
                    if (pendingDocuments.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            pendingDocuments.forEachIndexed { idx, (name, _) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        Modifier.padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Description, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                                        Icon(
                                            Icons.Default.Close, "移除",
                                            Modifier.size(14.dp).clickable { vm.removePendingDocument(idx) },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 对话建议（生成完成后推荐）
                    val suggestions = current?.chatSuggestions ?: emptyList()
                    if (suggestions.isNotEmpty() && !streaming) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)
                        ) {
                            suggestions.forEach { s ->
                                SuggestionChip(
                                    onClick = { vm.send(s) },
                                    label = { Text(s, fontSize = 12.sp, maxLines = 1) }
                                )
                            }
                        }
                    }
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isCtrlPressed &&
                                !(e.nativeKeyEvent as? java.awt.event.KeyEvent)?.isShiftDown!!
                            ) {
                                doSend(); true
                            } else false
                        },
                        placeholder = { Text("输入消息…（Enter 发送，Shift+Enter 换行）", fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        maxLines = 6,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 附件菜单（图片 / 上传文件）
                        var attachMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { attachMenu = true }) {
                                Icon(Icons.Default.Add, "附件", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("图片", fontSize = 13.sp) },
                                    onClick = { attachMenu = false; pickImage { vm.addPendingImage(it) } }
                                )
                                DropdownMenuItem(
                                    text = { Text("上传文件", fontSize = 13.sp) },
                                    onClick = { attachMenu = false; pickDocument { n, t -> vm.addPendingDocument(n, t) } }
                                )
                            }
                        }
                        // 快捷消息（点击插入输入框）
                        if (settings.quickMessages.isNotEmpty()) {
                            var qmMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { qmMenu = true }) {
                                    Icon(Icons.Default.Bolt, "快捷消息", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = qmMenu, onDismissRequest = { qmMenu = false }) {
                                    settings.quickMessages.forEach { qm ->
                                        DropdownMenuItem(
                                            text = { Text(qm.title.ifBlank { qm.content.take(20) }, fontSize = 12.sp) },
                                            onClick = { input = qm.content; qmMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        // 模型选择（显示生效模型：助手覆盖 > 全局）
                        if (provider != null && provider.models.isNotEmpty()) {
                            var modelMenu by remember { mutableStateOf(false) }
                            val effectiveModel = assistant?.chatModel ?: settings.activeModel
                            Box {
                                Row(
                                    Modifier.clickable { modelMenu = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        effectiveModel ?: "选择模型",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                    provider.models.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m, fontSize = 12.sp) },
                                            trailingIcon = {
                                                if (m == effectiveModel) {
                                                    Icon(Icons.Default.Check, "当前", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            onClick = { vm.switchModel(m); modelMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        // 推理力度（与 Android 版 ReasoningLevel 对齐）
                        run {
                            var effortMenu by remember { mutableStateOf(false) }
                            val currentEffort = REASONING_EFFORTS.firstOrNull { it.first == assistant?.reasoningEffort }?.second ?: "自动"
                            Box {
                                Row(
                                    Modifier.clickable { effortMenu = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("推理: $currentEffort", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                                    REASONING_EFFORTS.forEach { (effort, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, fontSize = 12.sp) },
                                            onClick = { vm.setAssistantReasoningEffort(effort); effortMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (streaming) {
                            FilledIconButton(onClick = { vm.stop() }, shape = CircleShape) {
                                Icon(Icons.Default.Stop, "停止", Modifier.size(18.dp))
                            }
                        } else {
                            FilledIconButton(
                                onClick = { doSend() },
                                enabled = input.isNotBlank() || pendingImages.isNotEmpty(),
                                shape = CircleShape
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 收藏夹（覆盖层） =====
    if (showFavorites) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FavoritesView(
                vm = vm,
                onBack = { showFavorites = false },
            )
        }
    }

    // ===== 翻译弹窗 =====
    translation?.let { result ->
        AlertDialog(
            onDismissRequest = { vm.dismissTranslation() },
            title = { Text("翻译") },
            text = {
                if (translationLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("翻译中…", fontSize = 13.sp)
                    }
                } else {
                    SelectionContainer { Text(result, fontSize = 14.sp, lineHeight = 22.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissTranslation() }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun FavoritesView(vm: ChatViewModel, onBack: () -> Unit) {
    val favorites = vm.favoriteMessages()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("收藏夹", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        if (favorites.isEmpty()) {
            Text("暂无收藏消息", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites) { (conv, msg, _) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.selectConversation(conv.id)
                        onBack()
                    }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                conv.title,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(formatTime(msg.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(msg.content, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
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
private fun MessageRow(
    message: ChatMessage,
    node: MessageNode?,
    streaming: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onEditResend: (String) -> Unit,
    onSelectBranch: (Int) -> Unit = {},
    userNickname: String = "用户",
    userAvatar: String = "",
    ds: DisplaySetting = DisplaySetting(),
    onTranslate: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }
    val bodyFontSize = 14.sp * ds.fontSizeRatio
    val bodyLineHeight = 22.sp * ds.fontSizeRatio

    if (isUser) {
        // ===== 用户消息（右对齐） =====
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(userNickname, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ds.showDateTimeInMessage) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatTime(message.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Spacer(Modifier.width(8.dp))
                UserAvatar(userNickname, userAvatar, 24.dp)
            }
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
                modifier = Modifier.widthIn(max = 680.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.imageUrls.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            message.imageUrls.forEach { url ->
                                DataUrlImage(url, Modifier.size(96.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)))
                            }
                        }
                        if (message.content.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (editing) {
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
                    } else if (message.content.isNotBlank()) {
                        Text(
                            message.content,
                            fontSize = bodyFontSize,
                            lineHeight = bodyLineHeight,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            MessageActions(
                onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                onEdit = if (!streaming && !editing) ({ editing = true; editText = message.content }) else null,
                onRegenerate = null,
                onDelete = if (!streaming) onDelete else null,
                onFavorite = if (!streaming) onToggleFavorite else null,
                favorite = message.favorite,
            )
            BranchSwitcher(node, onSelectBranch)
        }
    } else {
        // ===== AI 消息（左对齐，带头像） =====
        Row(Modifier.fillMaxWidth()) {
            // 头像：模型名首字符
            if (ds.showModelIcon) {
                Box(
                    Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (message.model ?: "AI").take(1).uppercase(),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ds.showModelName) {
                        Text(message.model ?: "assistant", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (ds.showDateTimeInMessage) {
                        if (ds.showModelName) Spacer(Modifier.width(8.dp))
                        Text(formatTime(message.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(4.dp))

                // 思考链（可折叠）
                if (ds.showThinkingContent && (message.reasoning.isNotBlank() || (streaming && message.content.isBlank()))) {
                    ThinkingCard(
                        reasoning = message.reasoning,
                        reasoningMs = message.reasoningMs,
                        thinking = streaming && message.content.isBlank(),
                        defaultCollapsed = ds.autoCloseThinking,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (message.content.isNotBlank() || !streaming) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 680.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            if (streaming && message.content.isBlank()) {
                                Text("▍", fontSize = bodyFontSize)
                            } else {
                                Markdown(
                                    content = message.content,
                                    colors = markdownColor(text = MaterialTheme.colorScheme.onSurface),
                                    typography = markdownTypography(
                                        text = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyFontSize, lineHeight = bodyLineHeight)
                                    ),
                                )
                            }
                        }
                    }
                }

                // token 用量 / 速度 / 耗时
                if (ds.showTokenUsage && (message.promptTokens != null || message.completionTokens != null)) {
                    Text(
                        buildString {
                            append("↑ ${message.promptTokens ?: 0} tokens · ↓ ${message.completionTokens ?: 0} tokens")
                            val genMs = message.generationMs
                            val out = message.completionTokens
                            if (genMs != null && genMs > 0 && out != null) {
                                append(" · ${"%.1f".format(out / (genMs / 1000.0))} tok/s")
                                append(" · ${"%.1f".format(genMs / 1000.0)}s")
                            }
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                MessageActions(
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onEdit = null,
                    onRegenerate = if (isLast && !streaming) onRegenerate else null,
                    onDelete = if (!streaming) onDelete else null,
                    onTranslate = if (!streaming && message.content.isNotBlank()) onTranslate else null,
                    onFavorite = if (!streaming) onToggleFavorite else null,
                    favorite = message.favorite,
                )
                BranchSwitcher(node, onSelectBranch)
            }
        }
    }
}

@Composable
private fun MessageActions(
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onTranslate: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    favorite: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
        IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.ContentCopy, "复制", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onTranslate != null) {
            IconButton(onClick = onTranslate, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Translate, "翻译", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onFavorite != null) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(26.dp)) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    if (favorite) "取消收藏" else "收藏",
                    Modifier.size(14.dp),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Edit, "编辑", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRegenerate != null) {
            IconButton(onClick = onRegenerate, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Refresh, "重新生成", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BranchSwitcher(node: MessageNode?, onSelectBranch: (Int) -> Unit) {
    if (node == null || node.messages.size <= 1) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onSelectBranch(node.selectIndex - 1) },
            enabled = node.selectIndex > 0,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(Icons.Default.ChevronLeft, "上一分支", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${node.selectIndex + 1}/${node.messages.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(
            onClick = { onSelectBranch(node.selectIndex + 1) },
            enabled = node.selectIndex < node.messages.size - 1,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(Icons.Default.ChevronRight, "下一分支", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThinkingCard(reasoning: String, reasoningMs: Long?, thinking: Boolean, defaultCollapsed: Boolean = true) {
    var expanded by remember { mutableStateOf(!defaultCollapsed) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.widthIn(max = 680.dp).clickable { expanded = !expanded }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        thinking -> "思考中…"
                        reasoningMs != null -> "思考了 ${"%.1f".format(reasoningMs / 1000.0)} 秒"
                        else -> "思考过程"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && reasoning.isNotBlank()) {
                Text(
                    reasoning,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DataUrlImage(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap = remember(dataUrl) { decodeDataUrl(dataUrl) }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier)
    }
}

/** 用户头像：有自定义头像显示图片，否则显示昵称首字符 */
@Composable
private fun UserAvatar(nickname: String, avatar: String, size: androidx.compose.ui.unit.Dp) {
    if (avatar.isNotBlank()) {
        DataUrlImage(avatar, Modifier.size(size).clip(CircleShape))
    } else {
        Box(
            Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                nickname.take(1).ifEmpty { "用" },
                fontSize = (size.value * 0.42).sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ProfileEditDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val settings = vm.settings
    var nickname by remember { mutableStateOf(settings.userNickname) }
    var avatar by remember { mutableStateOf(settings.userAvatar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("个人信息") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                UserAvatar(nickname.ifBlank { "用户" }, avatar, 64.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickAvatar { avatar = it } }) { Text("更换头像", fontSize = 12.sp) }
                    if (avatar.isNotBlank()) {
                        OutlinedButton(onClick = { avatar = "" }) { Text("重置", fontSize = 12.sp) }
                    }
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.updateSettings {
                    userNickname = nickname.ifBlank { "用户" }
                    userAvatar = avatar
                }
                vm.refreshSettings()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    else -> "晚上好"
}

private fun pickAvatar(onPicked: (String) -> Unit) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择头像", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.lowercase().let { n ->
            n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
        }
    }
    dialog.isVisible = true
    val fileName = dialog.file ?: return
    val bytes = File(dialog.directory, fileName).readBytes()
    onPicked(makeAvatarDataUrl(bytes))
}

/** 头像统一缩放为 128×128 PNG，避免 settings.json 里塞进大图 */
private fun makeAvatarDataUrl(bytes: ByteArray): String = runCatching {
    val src = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
    val size = 128
    val scaled = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = scaled.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
    )
    g.drawImage(src, 0, 0, size, size, null)
    g.dispose()
    val baos = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(scaled, "png", baos)
    "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray())
}.getOrElse {
    "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
}

private fun decodeDataUrl(dataUrl: String): ImageBitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(dataUrl.substringAfter(","))
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

private fun pickImage(onPicked: (String) -> Unit) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择图片", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.lowercase().let { n ->
            n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif")
        }
    }
    dialog.isVisible = true
    val fileName = dialog.file ?: return
    val file = File(dialog.directory, fileName)
    val bytes = file.readBytes()
    val mime = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/png"
    }
    onPicked("data:$mime;base64," + Base64.getEncoder().encodeToString(bytes))
}

/** 可作为提示词上传的文本类扩展名 */
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "csv", "xml", "yaml", "yml", "log", "toml", "ini", "conf", "properties",
    "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "html", "htm", "css", "sql", "sh", "bat",
    "c", "h", "cpp", "hpp", "go", "rs", "rb", "php", "swift", "vue", "gradle", "tex"
)

private fun pickDocument(onPicked: (String, String) -> Unit) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "上传文件", java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return
    val file = File(dialog.directory, fileName)
    if (file.extension.lowercase() !in TEXT_EXTENSIONS) return
    val text = runCatching { file.readText().take(100_000) }.getOrNull() ?: return
    onPicked(file.name, text)
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.CHINA).format(Date(ts))

private fun dayLabel(ts: Long): String {
    val today = LocalDate.now()
    val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
}
