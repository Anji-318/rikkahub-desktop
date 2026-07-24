package me.rerere.rikkahub.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import me.rerere.rikkahub.desktop.data.AppSettings

@Composable
fun RikkaTheme(
    darkTheme: Boolean? = null, // null=跟随系统
    themeId: String = "rikka",
    settings: AppSettings? = null, // themeId = "custom" 时取其中的 HSL 自定义配色
    content: @Composable () -> Unit,
) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    val preset = if (themeId == CUSTOM_THEME_ID && settings != null) {
        customThemePreset(settings)
    } else {
        findPresetTheme(themeId)
    }
    MaterialTheme(
        colorScheme = preset.getColorScheme(dark),
        content = content
    )
}
