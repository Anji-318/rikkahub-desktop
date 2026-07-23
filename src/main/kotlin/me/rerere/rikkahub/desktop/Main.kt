package me.rerere.rikkahub.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.rerere.rikkahub.desktop.ui.chat.ChatScreen
import me.rerere.rikkahub.desktop.ui.chat.ChatViewModel
import me.rerere.rikkahub.desktop.ui.settings.SettingsScreen
import me.rerere.rikkahub.desktop.ui.theme.RikkaTheme

fun main() = application {
    val vm = remember { ChatViewModel() }
    val settings by vm.settings.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "RikkaHub",
        state = rememberWindowState(
            position = WindowPosition.PlatformDefault,
            width = 1280.dp, height = 800.dp
        ),
    ) {
        window.minimumSize = java.awt.Dimension(960, 600)

        RikkaTheme(darkTheme = settings.darkTheme, themeId = settings.themeId) {
            if (showSettings) {
                SettingsScreen(vm = vm, onBack = { showSettings = false })
            } else {
                ChatScreen(vm = vm, onOpenSettings = { showSettings = true })
            }
        }
    }
}
