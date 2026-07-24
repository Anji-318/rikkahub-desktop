package me.rerere.rikkahub.desktop.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 会话内搜索条：关键词实时匹配当前会话消息，
 * 「上一个/下一个」在命中消息间循环跳转（滚动与高亮由外层处理）。
 */
@Composable
fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hitCount: Int,
    currentHit: Int, // 当前命中序号（0 起），-1 表示无命中
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索当前会话内容", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Spacer(Modifier.width(10.dp))
            // 命中计数（如 3/12）
            Text(
                if (hitCount > 0) "${currentHit + 1}/$hitCount" else "0/0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onPrev, enabled = hitCount > 0) {
                Icon(Icons.Default.KeyboardArrowUp, "上一个", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext, enabled = hitCount > 0) {
                Icon(Icons.Default.KeyboardArrowDown, "下一个", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "关闭搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 消息跳转器：浮动在消息区右下角的半透明竖排按钮组，
 * 支持跳到顶部 / 上一条 / 下一条 / 跳到底部（按消息节点为单位滚动）。
 */
@Composable
fun MessageJumper(
    showJumpTop: Boolean,
    showJumpBottom: Boolean,
    onJumpTop: () -> Unit,
    onPrevMessage: () -> Unit,
    onNextMessage: () -> Unit,
    onJumpBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(Modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showJumpTop) {
                IconButton(onClick = onJumpTop, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.KeyboardDoubleArrowUp, "跳到顶部", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onPrevMessage, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "上一条", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNextMessage, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "下一条", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showJumpBottom) {
                IconButton(onClick = onJumpBottom, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.KeyboardDoubleArrowDown, "跳到底部", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
