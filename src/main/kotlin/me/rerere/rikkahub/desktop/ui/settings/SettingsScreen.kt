package me.rerere.rikkahub.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import me.rerere.rikkahub.desktop.data.Assistant
import me.rerere.rikkahub.desktop.data.ProviderConfig
import me.rerere.rikkahub.desktop.data.QuickMessage
import me.rerere.rikkahub.desktop.ui.chat.ChatViewModel
import me.rerere.rikkahub.desktop.ui.theme.PresetThemes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val settings = vm.settings
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingAssistant by remember { mutableStateOf<Assistant?>(null) }
    var showAssistantEditor by remember { mutableStateOf(false) }
    var showQmEditor by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = { editing = null; showEditor = true }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("添加 Provider")
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("Providers", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
        ) {
            items(settings.providers, key = { it.id }) { p ->
                val active = p.id == settings.activeProviderId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 14.sp)
                            Text(p.baseUrl, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("模型: ${p.models.joinToString(", ").ifEmpty { "未配置" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (active) Icon(Icons.Default.Check, "当前", tint = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = {
                            vm.updateSettings {
                                activeProviderId = p.id
                                if (p.models.isNotEmpty()) activeModel = p.models.first()
                            }; vm.refreshSettings()
                        }) { Text("启用", fontSize = 12.sp) }
                        TextButton(onClick = { editing = p; showEditor = true }) { Text("编辑", fontSize = 12.sp) }
                        IconButton(onClick = {
                            vm.updateSettings { providers.removeAll { it.id == p.id } }; vm.refreshSettings()
                        }) { Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp)) }
                    }
                }
            }
        }

        // 助手设置
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("助手", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editingAssistant = null; showAssistantEditor = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加助手", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        settings.assistants.forEach { a ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp,
                color = if (a.id == settings.activeAssistantId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontSize = 13.sp)
                        Text(
                            buildString {
                                append(a.systemPrompt.replace("\n", " ").take(40).ifEmpty { "无系统提示词" })
                                a.chatModel?.let { append(" · $it") }
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (a.id == settings.activeAssistantId) {
                        Icon(Icons.Default.Check, "当前", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = {
                        vm.updateSettings { activeAssistantId = a.id }; vm.refreshSettings()
                    }) { Text("启用", fontSize = 12.sp) }
                    TextButton(onClick = { editingAssistant = a; showAssistantEditor = true }) { Text("编辑", fontSize = 12.sp) }
                    if (settings.assistants.size > 1) {
                        IconButton(onClick = {
                            vm.updateSettings {
                                assistants.removeAll { it.id == a.id }
                                if (activeAssistantId == a.id) activeAssistantId = assistants.first().id
                            }
                            vm.refreshSettings()
                        }) { Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp)) }
                    }
                }
            }
        }

        // 外观设置
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("外观", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "跟随系统", false to "浅色", true to "深色").forEach { (mode, label) ->
                FilterChip(
                    selected = settings.darkTheme == mode,
                    onClick = { vm.updateSettings { darkTheme = mode }; vm.refreshSettings() },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("主题配色", fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetThemes.forEach { preset ->
                FilterChip(
                    selected = settings.themeId == preset.id,
                    onClick = { vm.updateSettings { themeId = preset.id }; vm.refreshSettings() },
                    label = { Text(preset.name, fontSize = 12.sp) },
                    leadingIcon = {
                        Box(Modifier.size(12.dp).background(preset.standardLight.primary, CircleShape))
                    }
                )
            }
        }

        // 快捷消息
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("快捷消息", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showQmEditor = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加", fontSize = 12.sp)
            }
        }
        settings.quickMessages.forEach { qm ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(qm.title.ifBlank { "（无标题）" }, fontSize = 13.sp)
                        Text(qm.content, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        vm.updateSettings { quickMessages.removeAll { it.id == qm.id } }
                    }) { Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp)) }
                }
            }
        }

        // 默认模型分配
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("默认模型", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val modelPool = settings.providers.firstOrNull { it.id == settings.activeProviderId }?.models ?: emptyList()
        listOf(
            Triple("标题生成", settings.titleModelId, "title"),
            Triple("对话建议", settings.suggestionModelId, "suggestion"),
            Triple("翻译", settings.translateModelId, "translate"),
        ).forEach { (label, current, key) ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Box {
                    Row(
                        Modifier.clickable(enabled = modelPool.isNotEmpty()) { menu = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            current ?: "跟随聊天模型",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("跟随聊天模型", fontSize = 12.sp) },
                            onClick = {
                                vm.updateSettings {
                                    when (key) {
                                        "title" -> titleModelId = null
                                        "suggestion" -> suggestionModelId = null
                                        else -> translateModelId = null
                                    }
                                }
                                menu = false
                            }
                        )
                        modelPool.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, fontSize = 12.sp) },
                                trailingIcon = {
                                    if (m == current) {
                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    vm.updateSettings {
                                        when (key) {
                                            "title" -> titleModelId = m
                                            "suggestion" -> suggestionModelId = m
                                            else -> translateModelId = m
                                        }
                                    }
                                    menu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 联网搜索
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("联网搜索", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = settings.searchEnabled,
                onCheckedChange = { v -> vm.updateSettings { searchEnabled = v } }
            )
        }
        if (settings.searchEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf("tavily" to "Tavily", "exa" to "Exa").forEach { (svc, label) ->
                    FilterChip(
                        selected = settings.searchService == svc,
                        onClick = { vm.updateSettings { searchService = svc } },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
            var searchKey by remember(settings.searchApiKey) { mutableStateOf(settings.searchApiKey) }
            OutlinedTextField(
                value = searchKey,
                onValueChange = { searchKey = it },
                label = { Text("搜索服务 API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (searchKey != settings.searchApiKey) {
                        TextButton(onClick = { vm.updateSettings { searchApiKey = searchKey } }) { Text("保存", fontSize = 12.sp) }
                    }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("结果数: ${settings.searchResultSize}", fontSize = 13.sp, modifier = Modifier.width(90.dp))
                Slider(
                    value = settings.searchResultSize.toFloat(),
                    onValueChange = { v -> vm.updateSettings { searchResultSize = v.toInt() } },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                if (settings.searchService == "tavily") "API Key 申请：https://app.tavily.com/home" else "API Key 申请：https://dashboard.exa.ai",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 界面设置
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("界面", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val ds = settings.displaySetting
        Row(verticalAlignment = Alignment.CenterVertically) {
            var fontRatio by remember(ds.fontSizeRatio) { mutableStateOf(ds.fontSizeRatio) }
            Text("字号: ${"%.0f".format(fontRatio * 100)}%", fontSize = 13.sp, modifier = Modifier.width(90.dp))
            Slider(
                value = fontRatio,
                onValueChange = { fontRatio = it },
                onValueChangeFinished = {
                    vm.updateSettings { displaySetting = displaySetting.copy(fontSizeRatio = fontRatio) }
                },
                valueRange = 0.5f..2f,
                modifier = Modifier.weight(1f)
            )
        }
        listOf(
            Triple("消息时间戳", ds.showDateTimeInMessage, "dt"),
            Triple("模型头像", ds.showModelIcon, "icon"),
            Triple("模型名称", ds.showModelName, "name"),
            Triple("token 用量", ds.showTokenUsage, "token"),
            Triple("思考链", ds.showThinkingContent, "think"),
            Triple("思考链默认折叠", ds.autoCloseThinking, "fold"),
        ).forEach { (label, checked, key) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = { v ->
                        vm.updateSettings {
                            displaySetting = when (key) {
                                "dt" -> displaySetting.copy(showDateTimeInMessage = v)
                                "icon" -> displaySetting.copy(showModelIcon = v)
                                "name" -> displaySetting.copy(showModelName = v)
                                "token" -> displaySetting.copy(showTokenUsage = v)
                                "think" -> displaySetting.copy(showThinkingContent = v)
                                else -> displaySetting.copy(autoCloseThinking = v)
                            }
                        }
                    }
                )
            }
        }

        // 通用设置
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("通用", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        var sysPrompt by remember(settings) { mutableStateOf(settings.systemPrompt) }
        OutlinedTextField(
            value = sysPrompt,
            onValueChange = { sysPrompt = it },
            label = { Text("系统提示词（可选）") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            trailingIcon = {
                if (sysPrompt != settings.systemPrompt) {
                    TextButton(onClick = {
                        vm.updateSettings { systemPrompt = sysPrompt }
                        vm.refreshSettings()
                    }) { Text("保存", fontSize = 12.sp) }
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            var tempSlider by remember(settings.temperature) { mutableStateOf(settings.temperature.toFloat()) }
            Text("温度: ${"%.1f".format(tempSlider)}", fontSize = 13.sp, modifier = Modifier.width(90.dp))
            Slider(
                value = tempSlider,
                onValueChange = { tempSlider = it },
                onValueChangeFinished = {
                    vm.updateSettings { temperature = tempSlider.toDouble() }
                    vm.refreshSettings()
                },
                valueRange = 0f..2f,
                modifier = Modifier.weight(1f)
            )
        }
        // 模型选择
        val provider = settings.providers.firstOrNull { it.id == settings.activeProviderId }
        if (provider != null && provider.models.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("当前模型", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                provider.models.take(4).forEach { m ->
                    FilterChip(
                        selected = settings.activeModel == m,
                        onClick = { vm.updateSettings { activeModel = m }; vm.refreshSettings() },
                        label = { Text(m, fontSize = 11.sp) }
                    )
                }
            }
        }

        // 版本号（用于确认当前运行的构建版本）
        Spacer(Modifier.height(24.dp))
        Text(
            "RikkaHub Desktop v0.4.0",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }

    if (showQmEditor) {
        var qmTitle by remember { mutableStateOf("") }
        var qmContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQmEditor = false },
            title = { Text("添加快捷消息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(qmTitle, { qmTitle = it }, label = { Text("标题") }, singleLine = true)
                    OutlinedTextField(qmContent, { qmContent = it }, label = { Text("内容") }, maxLines = 4)
                }
            },
            confirmButton = {
                Button(
                    enabled = qmContent.isNotBlank(),
                    onClick = {
                        vm.updateSettings { quickMessages.add(QuickMessage(title = qmTitle, content = qmContent)) }
                        showQmEditor = false
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showQmEditor = false }) { Text("取消") } }
        )
    }

    if (showEditor) {
        ProviderEditorDialog(
            initial = editing,
            vm = vm,
            onDismiss = { showEditor = false },
            onSave = { p ->
                vm.updateSettings {
                    providers.removeAll { it.id == p.id }
                    providers.add(p)
                    if (activeProviderId == null) activeProviderId = p.id
                    if (activeModel == null && p.models.isNotEmpty()) activeModel = p.models.first()
                }
                vm.refreshSettings()
                showEditor = false
            }
        )
    }

    if (showAssistantEditor) {
        AssistantEditorDialog(
            initial = editingAssistant,
            models = settings.providers.firstOrNull { it.id == settings.activeProviderId }?.models ?: emptyList(),
            onDismiss = { showAssistantEditor = false },
            onSave = { a ->
                vm.updateSettings {
                    assistants.removeAll { it.id == a.id }
                    assistants.add(a)
                    if (activeAssistantId == null) activeAssistantId = a.id
                }
                vm.refreshSettings()
                showAssistantEditor = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantEditorDialog(
    initial: Assistant?,
    models: List<String>,
    onDismiss: () -> Unit,
    onSave: (Assistant) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var systemPrompt by remember { mutableStateOf(initial?.systemPrompt ?: "") }
    var chatModel by remember { mutableStateOf(initial?.chatModel) }
    var temperature by remember { mutableStateOf(initial?.temperature?.toString() ?: "") }
    var topP by remember { mutableStateOf(initial?.topP?.toString() ?: "") }
    var maxTokens by remember { mutableStateOf(initial?.maxTokens?.toString() ?: "") }
    var contextSize by remember { mutableStateOf(initial?.contextMessageSize?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加助手" else "编辑助手") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词（可选）") },
                    maxLines = 6,
                )
                if (models.isNotEmpty()) {
                    Text("绑定模型", fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = chatModel == null,
                            onClick = { chatModel = null },
                            label = { Text("跟随全局", fontSize = 11.sp) }
                        )
                        models.forEach { m ->
                            FilterChip(
                                selected = chatModel == m,
                                onClick = { chatModel = m },
                                label = { Text(m, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                Text("模型参数（留空跟随默认）", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("温度") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = topP,
                        onValueChange = { topP = it },
                        label = { Text("top_p") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("max_tokens") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = contextSize,
                        onValueChange = { contextSize = it },
                        label = { Text("上下文条数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Assistant(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            systemPrompt = systemPrompt,
                            chatModel = chatModel,
                            temperature = temperature.toDoubleOrNull(),
                            topP = topP.toDoubleOrNull(),
                            maxTokens = maxTokens.toIntOrNull(),
                            contextMessageSize = contextSize.toIntOrNull(),
                            reasoningEffort = initial?.reasoningEffort,
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ProviderEditorDialog(
    initial: ProviderConfig?,
    vm: ChatViewModel,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://api.deepseek.com/v1") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var models by remember { mutableStateOf(initial?.models?.joinToString(", ") ?: "") }
    var fetching by remember { mutableStateOf(false) }
    var fetchMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 Provider" else "编辑 Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL（OpenAI 兼容）") }, singleLine = true)
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, singleLine = true)
                OutlinedTextField(
                    value = models,
                    onValueChange = { models = it },
                    label = { Text("模型列表（逗号分隔）") },
                    trailingIcon = {
                        if (fetching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                )
                // 一键拉取模型列表
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !fetching && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                        onClick = {
                            fetching = true
                            fetchMsg = null
                            vm.fetchModelsRaw(baseUrl, apiKey) { list ->
                                fetching = false
                                if (list.isEmpty()) {
                                    fetchMsg = "拉取失败或无模型，请检查 Base URL 和 Key"
                                } else {
                                    models = list.joinToString(", ")
                                    fetchMsg = "已获取 ${list.size} 个模型"
                                }
                            }
                        }
                    ) { Text("获取模型列表", fontSize = 12.sp) }
                    fetchMsg?.let {
                        Spacer(Modifier.width(10.dp))
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("示例：deepseek-chat, deepseek-reasoner", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                onClick = {
                    onSave(
                        ProviderConfig(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name, baseUrl = baseUrl, apiKey = apiKey,
                            models = models.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
