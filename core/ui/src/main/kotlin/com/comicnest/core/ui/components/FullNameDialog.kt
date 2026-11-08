package com.comicnest.core.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * 全名弹窗（漫画阅读器 / 视频播放器共用）：
 * 完整展示名称（过长的文本可上下滚动），底部「复制」写入剪贴板；
 * 点击弹窗外部任意位置或「关闭」即消失。
 */
@Composable
fun FullNameDialog(
    title: String,
    name: String,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(name) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(name))
                    copied = true
                },
            ) {
                Text(if (copied) "已复制" else "复制")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("关闭") }
        },
    )
}
