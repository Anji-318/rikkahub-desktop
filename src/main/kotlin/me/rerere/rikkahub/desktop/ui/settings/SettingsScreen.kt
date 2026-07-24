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
import me.rerere.rikkahub.desktop.data.AssistantRegex
import me.rerere.rikkahub.desktop.data.KVEntry
import me.rerere.rikkahub.desktop.data.PresetMessage
import me.rerere.rikkahub.desktop.data.ProviderConfig
import me.rerere.rikkahub.desktop.data.QuickMessage
import me.rerere.rikkahub.desktop.data.StoragePaths
import me.rerere.rikkahub.desktop.ui.chat.ChatViewModel
import me.rerere.rikkahub.desktop.ui.theme.CUSTOM_THEME_ID
import me.rerere.rikkahub.desktop.ui.theme.PresetThemes
import me.rerere.rikkahub.desktop.ui.theme.customPrimaryColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val settings = vm.settings
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingAssistant by remember { mutableStateOf<Assistant?>(null) }
    var showAssistantEditor by remember { mutableStateOf(false) }
    var showQmEditor by remember { mutableStateOf(false) }
    // 备份与恢复：结果提示 + 待确认的导入路径
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var pendingImportPath by remember { mutableStateOf<String?>(null) }

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
            // 自定义主题（HSL 编辑器）
            FilterChip(
                selected = settings.themeId == CUSTOM_THEME_ID,
                onClick = { vm.updateSettings { themeId = CUSTOM_THEME_ID }; vm.refreshSettings() },
                label = { Text("自定义", fontSize = 12.sp) },
                leadingIcon = {
                    Box(Modifier.size(12.dp).background(
                        customPrimaryColor(settings.customPrimaryH, settings.customPrimaryS, settings.customPrimaryL, dark = false),
                        CircleShape
                    ))
                }
            )
        }
        // 选中「自定义」时展开 HSL 编辑器（拖动用局部状态，松手才持久化）
        if (settings.themeId == CUSTOM_THEME_ID) {
            Spacer(Modifier.height(8.dp))
            Text("主色", fontSize = 13.sp)
            HslSliders(
                h = settings.customPrimaryH, s = settings.customPrimaryS, l = settings.customPrimaryL,
                onFinished = { h, s, l ->
                    vm.updateSettings { customPrimaryH = h; customPrimaryS = s; customPrimaryL = l }
                }
            )
            Spacer(Modifier.height(4.dp))
            Text("背景色", fontSize = 13.sp)
            HslSliders(
                h = settings.customBackgroundH, s = settings.customBackgroundS, l = settings.customBackgroundL,
                onFinished = { h, s, l ->
                    vm.updateSettings { customBackgroundH = h; customBackgroundS = s; customBackgroundL = l }
                }
            )
            // 实时预览：主色在浅色/深色下的效果
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("预览", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(28.dp).background(
                    customPrimaryColor(settings.customPrimaryH, settings.customPrimaryS, settings.customPrimaryL, dark = false),
                    RoundedCornerShape(6.dp)
                ))
                Spacer(Modifier.width(4.dp))
                Text("浅色", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(28.dp).background(
                    customPrimaryColor(settings.customPrimaryH, settings.customPrimaryS, settings.customPrimaryL, dark = true),
                    RoundedCornerShape(6.dp)
                ))
                Spacer(Modifier.width(4.dp))
                Text("深色", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Triple("上下文压缩", settings.compressModelId, "compress"),
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
                                        "translate" -> translateModelId = null
                                        "compress" -> compressModelId = null
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
                                            "translate" -> translateModelId = m
                                            "compress" -> compressModelId = m
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

        // 压缩提示词
        var compressPromptInput by remember(settings.compressPrompt) { mutableStateOf(settings.compressPrompt) }
        OutlinedTextField(
            value = compressPromptInput,
            onValueChange = { compressPromptInput = it },
            label = { Text("压缩提示词") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (compressPromptInput != settings.compressPrompt) {
                    TextButton(onClick = { vm.updateSettings { compressPrompt = compressPromptInput } }) { Text("保存", fontSize = 12.sp) }
                }
            }
        )

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

        // 语音朗读 (TTS)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("语音朗读 (TTS)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = settings.ttsEnabled,
                onCheckedChange = { v -> vm.updateSettings { ttsEnabled = v } }
            )
        }
        if (settings.ttsEnabled) {
            var ttsModelInput by remember(settings.ttsModel) { mutableStateOf(settings.ttsModel) }
            OutlinedTextField(
                value = ttsModelInput,
                onValueChange = { ttsModelInput = it },
                label = { Text("TTS 模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (ttsModelInput != settings.ttsModel) {
                        TextButton(onClick = { vm.updateSettings { ttsModel = ttsModelInput } }) { Text("保存", fontSize = 12.sp) }
                    }
                }
            )
            var ttsVoiceInput by remember(settings.ttsVoice) { mutableStateOf(settings.ttsVoice) }
            OutlinedTextField(
                value = ttsVoiceInput,
                onValueChange = { ttsVoiceInput = it },
                label = { Text("发音人") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                trailingIcon = {
                    if (ttsVoiceInput != settings.ttsVoice) {
                        TextButton(onClick = { vm.updateSettings { ttsVoice = ttsVoiceInput } }) { Text("保存", fontSize = 12.sp) }
                    }
                }
            )
            Text(
                "使用当前 Provider 的 OpenAI 兼容接口（POST /audio/speech）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
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

        // 备份与恢复
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("备份与恢复", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    // AWT 文件选择器（SAVE 模式，默认带时间戳的文件名）
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "导出备份", java.awt.FileDialog.SAVE)
                    dialog.file = "rikkahub-backup-${SimpleDateFormat("yyyyMMdd-HHmm").format(Date())}.zip"
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val name = dialog.file
                    if (dir != null && name != null) {
                        val path = File(dir, if (name.endsWith(".zip")) name else "$name.zip").absolutePath
                        vm.exportBackupTo(path) { ok, msg -> backupMsg = msg }
                    }
                }
            ) { Text("导出备份", fontSize = 12.sp) }
            OutlinedButton(
                shape = RoundedCornerShape(10.dp),
                onClick = {
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "恢复备份", java.awt.FileDialog.LOAD)
                    dialog.file = "*.zip"
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val name = dialog.file
                    if (dir != null && name != null) {
                        pendingImportPath = File(dir, name).absolutePath
                    }
                }
            ) { Text("恢复备份", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "备份目录：${StoragePaths.root.absolutePath}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        backupMsg?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 版本号（用于确认当前运行的构建版本）
        Spacer(Modifier.height(24.dp))
        Text(
            "RikkaHub Desktop v0.5.0",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }

    // 恢复备份确认弹窗（会覆盖当前设置与全部会话）
    pendingImportPath?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingImportPath = null },
            title = { Text("恢复备份") },
            text = { Text("将覆盖当前设置与全部会话，此操作不可撤销。建议先导出备份。\n\n确定从该文件恢复吗？\n$path") },
            confirmButton = {
                Button(onClick = {
                    pendingImportPath = null
                    vm.importBackupFrom(path) { ok, msg -> backupMsg = msg }
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingImportPath = null }) { Text("取消") } }
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
            vm = vm,
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
    vm: ChatViewModel,
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
    var regexText by remember {
        mutableStateOf(initial?.regexes?.joinToString("\n") { "${it.scope}|${it.find}|${it.replace}" } ?: "")
    }
    var headersText by remember {
        mutableStateOf(initial?.customHeaders?.joinToString("\n") { "${it.key}: ${it.value}" } ?: "")
    }
    var bodiesText by remember {
        mutableStateOf(initial?.customBodies?.joinToString("\n") { "${it.key}: ${it.value}" } ?: "")
    }
    var messageTemplate by remember { mutableStateOf(initial?.messageTemplate ?: "") }
    var presetMessages by remember { mutableStateOf(initial?.presetMessages ?: emptyList()) }
    var enableMemory by remember { mutableStateOf(initial?.enableMemory ?: false) }
    var useGlobalMemory by remember { mutableStateOf(initial?.useGlobalMemory ?: false) }
    var showMemoryManager by remember { mutableStateOf(false) }

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
                // 消息模板（空=不启用）
                OutlinedTextField(
                    value = messageTemplate,
                    onValueChange = { messageTemplate = it },
                    label = { Text("消息模板（可选）") },
                    maxLines = 3,
                )
                Text(
                    "可用变量：{{ message }}、{{ role }}、{{ time }}、{{ date }}；留空不启用，只作用于发送内容",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 预置消息（开场白）：role 下拉 + 内容多行 + 删除/添加
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("预置消息（开场白）", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { presetMessages = presetMessages + PresetMessage() }) {
                        Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加", fontSize = 12.sp)
                    }
                }
                presetMessages.forEachIndexed { idx, pm ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var roleMenu by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                Modifier.clickable { roleMenu = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (pm.role == "assistant") "助手" else "用户",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                                listOf("user" to "用户", "assistant" to "助手").forEach { (role, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 12.sp) },
                                        onClick = {
                                            presetMessages = presetMessages.mapIndexed { i, p ->
                                                if (i == idx) p.copy(role = role) else p
                                            }
                                            roleMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { presetMessages = presetMessages.filterIndexed { i, _ -> i != idx } },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp))
                        }
                    }
                    OutlinedTextField(
                        value = pm.content,
                        onValueChange = { v ->
                            presetMessages = presetMessages.mapIndexed { i, p ->
                                if (i == idx) p.copy(content = v) else p
                            }
                        },
                        label = { Text("内容") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                OutlinedTextField(
                    value = headersText,
                    onValueChange = { headersText = it },
                    label = { Text("自定义请求头（每行 Key: Value）") },
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = bodiesText,
                    onValueChange = { bodiesText = it },
                    label = { Text("自定义请求体（每行 key: value，支持 JSON）") },
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = regexText,
                    onValueChange = { regexText = it },
                    label = { Text("正则变换（每行 input|查找|替换 或 output|查找|替换）") },
                    maxLines = 4,
                )
                // 记忆：开启后注入记忆段并注册 memory 工具，模型在对话中自主读写
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记忆（长期记忆）", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = enableMemory, onCheckedChange = { enableMemory = it })
                }
                if (enableMemory) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("使用全局记忆（全助手共享）", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = useGlobalMemory, onCheckedChange = { useGlobalMemory = it })
                    }
                    if (initial != null) {
                        OutlinedButton(onClick = { showMemoryManager = true }) {
                            Text("管理记忆", fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            "保存助手后可管理记忆",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            customHeaders = parseKvLines(headersText),
                            customBodies = parseKvLines(bodiesText),
                            regexes = parseRegexLines(regexText),
                            presetMessages = presetMessages.filter { it.content.isNotBlank() },
                            messageTemplate = messageTemplate,
                            enableMemory = enableMemory,
                            useGlobalMemory = useGlobalMemory,
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // 管理记忆对话框：列表（编辑/删除）+ 顶部添加框
    if (showMemoryManager && initial != null) {
        MemoryManagerDialog(
            vm = vm,
            assistantId = initial.id,
            onDismiss = { showMemoryManager = false },
        )
    }
}

/** 记忆管理对话框：记忆列表（内容可编辑 + 删除）+ 顶部添加框，CRUD 走 VM 桥接（按助手归属） */
@Composable
private fun MemoryManagerDialog(
    vm: ChatViewModel,
    assistantId: String,
    onDismiss: () -> Unit,
) {
    var memories by remember { mutableStateOf(vm.listMemories(assistantId)) }
    var newContent by remember { mutableStateOf("") }
    fun refresh() { memories = vm.listMemories(assistantId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理记忆") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("添加记忆") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newContent.isNotBlank()) {
                                vm.createMemory(assistantId, newContent.trim())
                                newContent = ""
                                refresh()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
                if (memories.isEmpty()) {
                    Text(
                        "暂无记忆，开启记忆后模型会在对话中自动积累",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                memories.forEach { m ->
                    var editContent by remember(m.id, m.updatedAt) { mutableStateOf(m.content) }
                    Column {
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = editContent.isNotBlank() && editContent != m.content,
                                onClick = {
                                    vm.updateMemory(assistantId, m.id, editContent.trim())
                                    refresh()
                                }
                            ) { Text("保存", fontSize = 12.sp) }
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    vm.deleteMemory(assistantId, m.id)
                                    refresh()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

/** HSL 三滑杆编辑器：拖动用局部 remember 状态，松手（onValueChangeFinished）才回调持久化 */
@Composable
private fun HslSliders(
    h: Float,
    s: Float,
    l: Float,
    onFinished: (Float, Float, Float) -> Unit,
) {
    var hue by remember(h) { mutableStateOf(h) }
    var sat by remember(s) { mutableStateOf(s) }
    var lig by remember(l) { mutableStateOf(l) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("色相: ${"%.0f".format(hue)}", fontSize = 12.sp, modifier = Modifier.width(90.dp))
            Slider(
                value = hue,
                onValueChange = { hue = it },
                onValueChangeFinished = { onFinished(hue, sat, lig) },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("饱和: ${"%.2f".format(sat)}", fontSize = 12.sp, modifier = Modifier.width(90.dp))
            Slider(
                value = sat,
                onValueChange = { sat = it },
                onValueChangeFinished = { onFinished(hue, sat, lig) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("亮度: ${"%.2f".format(lig)}", fontSize = 12.sp, modifier = Modifier.width(90.dp))
            Slider(
                value = lig,
                onValueChange = { lig = it },
                onValueChangeFinished = { onFinished(hue, sat, lig) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 解析 "Key: Value" 行式文本 */
private fun parseKvLines(text: String) = text.lines().mapNotNull { line ->
    val idx = line.indexOf(':')
    if (idx <= 0) return@mapNotNull null
    KVEntry(key = line.substring(0, idx).trim(), value = line.substring(idx + 1).trim())
}

/** 解析 "scope|find|replace" 行式文本 */
private fun parseRegexLines(text: String) = text.lines().mapNotNull { line ->
    val parts = line.split("|", limit = 3)
    if (parts.size != 3 || parts[1].isBlank()) return@mapNotNull null
    AssistantRegex(
        scope = if (parts[0].trim() == "input") "input" else "output",
        find = parts[1],
        replace = parts[2],
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
    var type by remember { mutableStateOf(initial?.type ?: "openai") }
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
                // 协议类型
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("openai" to "OpenAI 兼容", "claude" to "Claude", "google" to "Gemini").forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (initial == null) {
                                    baseUrl = when (t) {
                                        "claude" -> "https://api.anthropic.com"
                                        "google" -> "https://generativelanguage.googleapis.com"
                                        else -> baseUrl
                                    }
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true)
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
                            vm.fetchModelsRaw(baseUrl, apiKey, type) { list ->
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
                            models = models.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() },
                            type = type,
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
