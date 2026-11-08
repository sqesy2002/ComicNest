package com.comicnest.feature.video.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.comicnest.core.common.util.VideoFileNames
import com.comicnest.core.model.VideoSummary

/**
 * 重命名弹窗：只编辑主名，扩展名固定展示且不可改（避免破坏扫描白名单与播放）。
 * 输入实时校验；确认按钮仅在名称合法且确实发生变化时可用。
 */
@Composable
fun VideoRenameDialog(
    target: VideoSummary,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val oldFileName = target.key.fileName
    val originalBase = remember(oldFileName) { VideoFileNames.baseNameOf(oldFileName) }
    val extension = remember(oldFileName) { VideoFileNames.extensionOf(oldFileName) }
    var input by remember(oldFileName) { mutableStateOf(originalBase) }

    val validation = VideoFileNames.validateBaseName(input)
    val base = validation.getOrNull()
    val unchanged = base != null && base == originalBase
    val error = validation.exceptionOrNull()?.message
    val suffixContent: (@Composable () -> Unit)? = if (extension.isEmpty()) {
        null
    } else {
        { Text(".${extension}") }
    }

    fun submit() {
        val value = base ?: return
        if (unchanged) return
        onConfirm(value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名视频") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("视频名称") },
                    suffix = suffixContent,
                    isError = error != null,
                    supportingText = {
                        Text(
                            text = when {
                                error != null -> error
                                unchanged -> "名称未发生变化"
                                extension.isEmpty() -> "文件将重命名为「${base}」"
                                else -> "文件将重命名为「${base}.${extension}」"
                            },
                            color = if (error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (extension.isEmpty()) {
                        "只修改视频名称。"
                    } else {
                        "只修改名称，扩展名 .${extension} 保持不变，以免影响播放与扫描。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = base != null && !unchanged) {
                Text("重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
