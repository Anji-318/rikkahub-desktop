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

    /** 保证至少有一个助手，并修正 activeAssistantId */
    private fun normalize(s: AppSettings) {
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
