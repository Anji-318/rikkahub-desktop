package me.rerere.rikkahub.desktop.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.desktop.data.AppSettings
import kotlin.math.abs

/** 自定义主题的 themeId 约定值 */
const val CUSTOM_THEME_ID = "custom"

/** 标准 HSL(0~360, 0~1, 0~1) → Compose Color（注意 HSB≠HSL，这里用手写 HSL 公式） */
fun hslToColor(h: Float, s: Float, l: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s.coerceIn(0f, 1f)
    val ll = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * ll - 1f)) * ss
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = ll - c / 2f
    val (r, g, b) = when {
        hh < 60f -> Triple(c, x, 0f)
        hh < 120f -> Triple(x, c, 0f)
        hh < 180f -> Triple(0f, c, x)
        hh < 240f -> Triple(0f, x, c)
        hh < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m, alpha = 1f)
}

/** 自定义主色（dark=true 时适当提亮到 L 0.65~0.75，与 customThemePreset 一致；设置页预览也用它） */
fun customPrimaryColor(h: Float, s: Float, l: Float, dark: Boolean): Color =
    if (dark) hslToColor(h, s, (l + 0.15f).coerceIn(0.65f, 0.75f)) else hslToColor(h, s, l)

/**
 * 由设置里的 custom*HSL 生成自定义主题（亮/暗两套 ColorScheme）。
 * 派生色参照 rikka 主题的写法：浅色底深字、深色底浅字，
 * dark 背景用同一色相压低亮度（L≈0.09~0.17）。
 */
fun customThemePreset(s: AppSettings): PresetTheme {
    val ph = s.customPrimaryH
    val ps = s.customPrimaryS
    val pl = s.customPrimaryL
    val bh = s.customBackgroundH
    val bs = s.customBackgroundS
    val bl = s.customBackgroundL

    val light = lightColorScheme(
        primary = customPrimaryColor(ph, ps, pl, dark = false),
        onPrimary = Color.White,
        primaryContainer = hslToColor(ph, ps * 0.5f, 0.92f),
        onPrimaryContainer = hslToColor(ph, ps, 0.25f),
        background = hslToColor(bh, bs, bl),
        surface = hslToColor(bh, bs * 0.5f, (bl + 0.03f).coerceAtMost(1f)),
        surfaceVariant = hslToColor(bh, bs, (bl - 0.05f).coerceIn(0f, 1f)),
        onBackground = Color(0xFF2C2A26),
        onSurface = Color(0xFF2C2A26),
        onSurfaceVariant = Color(0xFF6F6A61),
        outline = hslToColor(bh, bs, (bl - 0.12f).coerceIn(0f, 1f)),
    )
    val dark = darkColorScheme(
        primary = customPrimaryColor(ph, ps, pl, dark = true),
        onPrimary = hslToColor(ph, ps, 0.15f),
        primaryContainer = hslToColor(ph, ps * 0.6f, 0.3f),
        onPrimaryContainer = hslToColor(ph, ps * 0.5f, 0.9f),
        background = hslToColor(bh, (bs * 0.6f).coerceAtLeast(0.05f), 0.09f),
        surface = hslToColor(bh, (bs * 0.6f).coerceAtLeast(0.05f), 0.12f),
        surfaceVariant = hslToColor(bh, (bs * 0.6f).coerceAtLeast(0.05f), 0.17f),
        onBackground = Color(0xFFE8E4DD),
        onSurface = Color(0xFFE8E4DD),
        onSurfaceVariant = Color(0xFFB0A99E),
        outline = hslToColor(bh, (bs * 0.6f).coerceAtLeast(0.05f), 0.28f),
    )
    return PresetTheme(id = CUSTOM_THEME_ID, name = "自定义", standardLight = light, standardDark = dark)
}
