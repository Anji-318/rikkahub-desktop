package me.rerere.rikkahub.desktop.data

import kotlinx.serialization.json.Json
import java.io.File

/** 全局记忆使用的固定助手 id（useGlobalMemory 时全助手共享） */
const val GLOBAL_MEMORY_ID = "__global__"

/** 记忆存储：每助手一个 JSON 文件 db/memory/{assistantId}.json */
class MemoryStore {
    private val dir = File(StoragePaths.dbDir, "memory").also { it.mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileOf(assistantId: String) = File(dir, "$assistantId.json")

    @Synchronized
    fun list(assistantId: String): List<MemoryEntry> =
        fileOf(assistantId).takeIf { it.exists() }?.let {
            runCatching { json.decodeFromString<List<MemoryEntry>>(it.readText()) }.getOrNull()
        } ?: emptyList()

    private fun saveAll(assistantId: String, entries: List<MemoryEntry>) {
        fileOf(assistantId).writeText(json.encodeToString(entries))
    }

    @Synchronized
    fun create(assistantId: String, content: String): MemoryEntry {
        val entry = MemoryEntry(content = content, updatedAt = System.currentTimeMillis())
        saveAll(assistantId, list(assistantId) + entry)
        return entry
    }

    /** 更新成功返回 true，id 不存在返回 false */
    @Synchronized
    fun update(assistantId: String, id: String, content: String): Boolean {
        val entries = list(assistantId)
        if (entries.none { it.id == id }) return false
        saveAll(
            assistantId,
            entries.map {
                if (it.id == id) it.copy(content = content, updatedAt = System.currentTimeMillis()) else it
            }
        )
        return true
    }

    /** 删除成功返回 true，id 不存在返回 false */
    @Synchronized
    fun delete(assistantId: String, id: String): Boolean {
        val entries = list(assistantId)
        if (entries.none { it.id == id }) return false
        saveAll(assistantId, entries.filterNot { it.id == id })
        return true
    }
}
