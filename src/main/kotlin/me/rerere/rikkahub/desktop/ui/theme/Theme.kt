package me.rerere.rikkahub.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun RikkaTheme(
    darkTheme: Boolean? = null, // null=跟随系统
    themeId: String = "rikka",
    content: @Composable () -> Unit,
) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = findPresetTheme(themeId).getColorScheme(dark),
        content = content
    )
}
