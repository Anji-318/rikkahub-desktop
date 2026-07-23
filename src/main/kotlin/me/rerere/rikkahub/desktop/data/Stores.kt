package me.rerere.rikkahub.desktop.data

import kotlinx.serialization.json.Json
import java.io.File

/** 跨平台数据目录（替代 Android Context.filesDir） */
object StoragePaths {
    val root: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "rikkahub")
            os.contains("mac") -> File(home, "Library/Application Support/rikkahub")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "rikkahub")
        }.also { r ->
            listOf(r, File(r, "db"), File(r, "db/conversations"), File(r, "files")).forEach { it.mkdirs() }
        }
    }
    val dbDir get() = File(root, "db")
}

class SettingsStore {
    private val file = File(StoragePaths.dbDir, "settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    var settings: AppSettings = load()
        private set

    private fun load(): AppSettings = runCatching {
        if (file.exists()) json.decodeFromString<AppSettings>(file.readText()) else AppSettings()
    }.getOrElse { AppSettings() }.also { normalize(it) }

    /**
     * 启动时合并预置数据（保留用户已填的 apiKey、自建 Provider/助手）：
     * 1. 补齐缺失的预置 Provider 及其模型
     * 2. 补齐缺失的内置助手
     * 3. 修正 activeProviderId / activeModel / activeAssistantId
     */
    private fun normalize(s: AppSettings) {
        // Provider：缺的补整个，已有的补缺的模型
        val mergedProviders = s.providers.map { user ->
            val preset = DEFAULT_PROVIDERS.find { it.id == user.id } ?: return@map user
            val missingModels = preset.models.filter { it !in user.models }
            if (missingModels.isEmpty()) user else user.copy(models = user.models + missingModels)
        }.toMutableList()
        DEFAULT_PROVIDERS.forEach { preset ->
            if (mergedProviders.none { it.id == preset.id }) mergedProviders.add(preset)
        }
        if (mergedProviders != s.providers) {
            s.providers.clear()
            s.providers.addAll(mergedProviders)
        }
        if (s.activeProviderId == null || s.providers.none { it.id == s.activeProviderId }) {
            s.activeProviderId = s.providers.firstOrNull()?.id
        }
        if (s.activeModel == null) {
            s.activeModel = s.providers.firstOrNull { it.id == s.activeProviderId }?.models?.firstOrNull()
        }

        // 助手：补齐内置助手（首次启动把"默认助手"替换为预置组）
        if (s.assistants.size == 1 && s.assistants[0].name == "默认助手" && s.assistants[0].systemPrompt.isEmpty()) {
            s.assistants.clear()
        }
        BUILT_IN_ASSISTANTS.forEach { preset ->
            if (s.assistants.none { it.id == preset.id }) s.assistants.add(preset)
        }
        if (s.assistants.isEmpty()) {
            s.assistants.add(Assistant(name = "默认助手"))
        }
        if (s.activeAssistantId == null || s.assistants.none { it.id == s.activeAssistantId }) {
            s.activeAssistantId = s.assistants.first().id
        }
    }

    @Synchronized
    fun update(block: AppSettings.() -> Unit): AppSettings {
        settings.apply(block)
        settings.rev += 1
        file.writeText(json.encodeToString(AppSettings.serializer(), settings))
        return settings
    }

    fun activeProvider(): ProviderConfig? =
        settings.providers.firstOrNull { it.id == settings.activeProviderId && it.enabled }
            ?: settings.providers.firstOrNull { it.enabled }
}

class ConversationStore {
    private val dir = File(StoragePaths.dbDir, "conversations").also { it.mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileOf(id: String) = File(dir, "$id.json")

    fun list(): List<Conversation> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<Conversation>(it.readText()) }.getOrNull() }
            ?.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
            ?: emptyList()

    fun get(id: String): Conversation? =
        fileOf(id).takeIf { it.exists() }?.let {
            runCatching { json.decodeFromString<Conversation>(it.readText()) }.getOrNull()
        }

    @Synchronized
    fun save(c: Conversation): Conversation {
        c.updatedAt = System.currentTimeMillis()
        fileOf(c.id).writeText(json.encodeToString(Conversation.serializer(), c))
        return c
    }

    fun create(title: String = "新对话", assistantId: String? = null) =
        save(Conversation(title = title, assistantId = assistantId))
    fun delete(id: String) = fileOf(id).delete()
}
