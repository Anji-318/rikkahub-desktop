package me.rerere.rikkahub.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.rerere.rikkahub.desktop.data.StoragePaths
import me.rerere.rikkahub.desktop.ui.chat.ChatScreen
import me.rerere.rikkahub.desktop.ui.chat.ChatViewModel
import me.rerere.rikkahub.desktop.ui.settings.SettingsScreen
import me.rerere.rikkahub.desktop.ui.theme.RikkaTheme
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

private var instanceLock: FileLock? = null

/** 单实例锁：防止多个实例并发读写同一份数据（也避免误以为在操作新版本） */
private fun tryAcquireInstanceLock(): Boolean = runCatching {
    val lockFile = File(StoragePaths.dbDir, ".lock")
    instanceLock = RandomAccessFile(lockFile, "rw").channel.tryLock()
    instanceLock != null
}.getOrDefault(false)

fun main() {
    if (!tryAcquireInstanceLock()) {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "RikkaHub 已在运行中。\n如需使用新版本，请先在任务管理器中结束所有 RikkaHub.exe 进程。",
            "RikkaHub",
            javax.swing.JOptionPane.WARNING_MESSAGE
        )
        return
    }
    application {
        val vm = remember { ChatViewModel() }
        val settings = vm.settings
        var showSettings by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "RikkaHub v$APP_VERSION",
            icon = painterResource("icon.png"),
            state = rememberWindowState(
                position = WindowPosition.PlatformDefault,
                width = 1280.dp, height = 800.dp
            ),
        ) {
            window.minimumSize = java.awt.Dimension(960, 600)

            RikkaTheme(darkTheme = settings.darkTheme, themeId = settings.themeId, settings = settings) {
                if (showSettings) {
                    SettingsScreen(vm = vm, onBack = { showSettings = false })
                } else {
                    ChatScreen(vm = vm, onOpenSettings = { showSettings = true })
                }
            }
        }
    }
}
