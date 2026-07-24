package me.rerere.rikkahub.desktop.data

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 本地备份/恢复（对齐安卓 ImportExportTab 思路）：
 * 把 dbDir 下的 settings.json 与 conversations/ 目录全部会话打进一个 zip。
 */
object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 导出备份：zip 内条目保持相对路径（settings.json、conversations/xxx.json） */
    fun exportBackup(target: File) {
        val dbDir = StoragePaths.dbDir
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            fun addFile(file: File, entryName: String) {
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            val settingsFile = File(dbDir, "settings.json")
            if (settingsFile.exists()) addFile(settingsFile, "settings.json")
            File(dbDir, "conversations").listFiles { f -> f.extension == "json" }?.forEach { f ->
                addFile(f, "conversations/${f.name}")
            }
        }
    }

    /**
     * 恢复备份：先解压到临时目录校验（zip 内必须有 settings.json，且所有 JSON 可解析），
     * 校验通过后才覆盖 dbDir 对应文件；失败抛异常（message 为失败原因）。
     */
    fun importBackup(source: File) {
        val tempDir = Files.createTempDirectory("rikkahub-import").toFile()
        try {
            // 1. 解压到临时目录（只认 settings.json 与 conversations/*.json，防路径穿越）
            ZipFile(source).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                if ("settings.json" !in names) error("备份文件缺少 settings.json")
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    val name = entry.name
                    val valid = name == "settings.json" ||
                        (name.startsWith("conversations/") && name.endsWith(".json") && !name.contains(".."))
                    if (!valid) return@forEach
                    val out = File(tempDir, name)
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            // 2. 校验 JSON 可解析（与 Stores.kt 相同的 Json 配置）
            runCatching { json.decodeFromString<AppSettings>(File(tempDir, "settings.json").readText()) }
                .onFailure { error("settings.json 解析失败: ${it.message}") }
            File(tempDir, "conversations").listFiles { f -> f.extension == "json" }?.forEach { f ->
                runCatching { json.decodeFromString<Conversation>(f.readText()) }
                    .onFailure { error("会话文件 ${f.name} 解析失败: ${it.message}") }
            }
            // 3. 校验通过，覆盖 dbDir（会话全量替换：先清空再写入）
            val dbDir = StoragePaths.dbDir
            File(tempDir, "settings.json").copyTo(File(dbDir, "settings.json"), overwrite = true)
            val convDir = File(dbDir, "conversations").also { it.mkdirs() }
            convDir.listFiles { f -> f.extension == "json" }?.forEach { it.delete() }
            File(tempDir, "conversations").listFiles { f -> f.extension == "json" }?.forEach { f ->
                f.copyTo(File(convDir, f.name), overwrite = true)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
