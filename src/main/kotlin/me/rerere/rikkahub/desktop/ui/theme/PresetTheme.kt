package me.rerere.rikkahub.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.desktop.ui.theme.presets.AutumnThemePreset
import me.rerere.rikkahub.desktop.ui.theme.presets.BlackThemePreset
import me.rerere.rikkahub.desktop.ui.theme.presets.OceanThemePreset
import me.rerere.rikkahub.desktop.ui.theme.presets.SakuraThemePreset
import me.rerere.rikkahub.desktop.ui.theme.presets.SpringThemePreset

data class PresetTheme(
    val id: String,
    val name: String,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme = if (dark) standardDark else standardLight
}

// 默认暖色主题（与 RikkaHub 风格一致）
private val RikkaLightColors = lightColorScheme(
    primary = Color(0xFFB5542D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E7DE),
    onPrimaryContainer = Color(0xFF7A3418),
    secondary = Color(0xFF8A7360),
    background = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EFE9),
    onBackground = Color(0xFF2C2A26),
    onSurface = Color(0xFF2C2A26),
    onSurfaceVariant = Color(0xFF6F6A61),
    outline = Color(0xFFE8E2D9),
)

private val RikkaDarkColors = darkColorScheme(
    primary = Color(0xFFE08A63),
    onPrimary = Color(0xFF3D1B0C),
    primaryContainer = Color(0xFF5C2E1A),
    onPrimaryContainer = Color(0xFFF6D9CB),
    secondary = Color(0xFFB8A493),
    background = Color(0xFF1E1C19),
    surface = Color(0xFF27241F),
    surfaceVariant = Color(0xFF332F29),
    onBackground = Color(0xFFE8E4DD),
    onSurface = Color(0xFFE8E4DD),
    onSurfaceVariant = Color(0xFFB0A99E),
    outline = Color(0xFF4A453D),
)

val RikkaThemePreset = PresetTheme(
    id = "rikka",
    name = "暖橙",
    standardLight = RikkaLightColors,
    standardDark = RikkaDarkColors,
)

val PresetThemes by lazy {
    listOf(
        RikkaThemePreset,
        SakuraThemePreset,
        OceanThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
    )
}

fun findPresetTheme(id: String): PresetTheme =
    PresetThemes.find { it.id == id } ?: RikkaThemePreset
