package me.rerere.rikkahub.desktop.llm

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 替换单个变量：支持 {{key}} 与 {key} 两种写法，大小写不敏感 */
private fun replaceVar(text: String, key: String, value: String): String =
    Regex("\\{\\{?\\s*$key\\s*\\}?\\}", RegexOption.IGNORE_CASE).replace(text) { value }

/**
 * 提示词变量替换（对齐安卓 PlaceholderTransformer）。
 * 作用于 systemPrompt 与 system 消息，用户消息不替换。
 */
fun applyPromptVariables(
    text: String,
    model: String?,
    modelDisplayName: String?,
    assistantName: String?,
    userNickname: String,
): String {
    if (text.isEmpty()) return text
    val now = Date()
    val vars = mapOf(
        "cur_date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
        "cur_time" to SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now),
        "cur_datetime" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now),
        "model_id" to (model ?: ""),
        // 桌面版没有单独的模型显示名，与 id 一致
        "model_name" to (modelDisplayName ?: model ?: ""),
        "locale" to Locale.getDefault().toString(),
        "timezone" to TimeZone.getDefault().id,
        "system_version" to "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        "device_info" to (runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?: System.getenv("COMPUTERNAME") ?: "Desktop"),
        "battery_level" to "N/A", // 桌面端无电量概念
        "nickname" to userNickname,
        "user" to userNickname,
        "char" to (assistantName ?: ""),
    )
    var result = text
    vars.forEach { (key, value) -> result = replaceVar(result, key, value) }
    return result
}

/**
 * 消息模板渲染（简单变量替换，不用模板引擎）：
 * {{ message }}=原文，{{ role }}=角色，{{ time }}/{{ date }}=消息时间。
 * 只作用于发送给 API 的内容，不改本地存储。
 */
fun applyMessageTemplate(template: String, message: String, role: String, createdAt: Long): String {
    if (template.isBlank()) return message
    val d = Date(createdAt)
    var result = template
    result = replaceVar(result, "message", message)
    result = replaceVar(result, "role", role)
    result = replaceVar(result, "time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(d))
    result = replaceVar(result, "date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d))
    return result
}
