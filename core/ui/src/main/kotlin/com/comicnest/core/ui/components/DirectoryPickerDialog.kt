package com.comicnest.core.ui.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 浏览式目录选择器（替代手填绝对路径）：从外部存储根开始，逐层点选文件夹。
 * 基于 `java.io.File`，与绝对路径模型（D2）一致。右上角可在「浏览 / 手动输入」间切换，
 * 保留手填兜底。空库根需满足二级结构（其子目录才是漫画）。
 *
 * 注意：Android 11+ 即便授予「所有文件访问权限」，`Android/data`、`Android/obb` 仍被系统屏蔽，
 * 进入这类目录会提示不可访问。
 *
 * @param onPick 选定目录后回调其绝对路径。
 * @param onDismissRequest 取消/关闭时回调（用于隐藏对话框）。
 * @param initialPath 初始定位目录，缺省为外部存储根。
 */
@Composable
fun DirectoryPickerDialog(
    onPick: (String) -> Unit,
    onDismissRequest: () -> Unit,
    initialPath: String? = null,
    title: String = "选择漫画目录",
) {
    val startDir = initialPath?.let { File(it) }?.takeIf { it.isDirectory } ?: defaultExternalRoot()
    var currentPath by rememberSaveable { mutableStateOf(startDir.absolutePath) }
    val current = remember(currentPath) { File(currentPath) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var manualText by rememberSaveable { mutableStateOf("") }
    var accessError by remember { mutableStateOf<String?>(null) }

    val children = runCatching {
        current.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (manual) {
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        placeholder = { Text("/storage/emulated/0/Comics") },
                        label = { Text("输入绝对路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 当前路径 + 返回上级
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val canGoUp = current.parentFile != null
                        IconButton(onClick = { current.parentFile?.let { currentPath = it.absolutePath } }, enabled = canGoUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上级目录")
                        }
                        Text(
                            text = current.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "提示：请选择漫画的「上层目录」，其每个子目录识别为一部漫画；" +
                            "Android/data 等系统受限目录无法读取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    accessError?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    when {
                        children == null -> Text("无法读取该目录（可能被系统限制）", style = MaterialTheme.typography.bodyMedium)
                        children.isEmpty() -> Text("该目录下没有子文件夹", style = MaterialTheme.typography.bodyMedium)
                        else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(children, key = { it.absolutePath }) { dir ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (dir.canRead()) {
                                                accessError = null
                                                currentPath = dir.absolutePath
                                            } else {
                                                accessError = restrictedHint(dir)
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = dir.name.ifBlank { dir.absolutePath },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (manual) {
                        val p = manualText.trim()
                        if (p.isNotEmpty()) onPick(p)
                    } else {
                        onPick(current.absolutePath)
                    }
                },
                enabled = manual && manualText.isBlank().not() || !manual,
            ) { Text(if (manual) "确定" else "选择此目录") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    manual = !manual
                    accessError = null
                },
            ) { Text(if (manual) "浏览目录" else "手动输入") }
        },
    )
}

private fun defaultExternalRoot(): File {
    val external = Environment.getExternalStorageDirectory()
    return external.takeIf { it.isDirectory } ?: File("/storage/emulated/0")
}

private fun restrictedHint(dir: File): String =
    if (dir.absolutePath.contains("/Android/data/") || dir.absolutePath.contains("/Android/obb/")) {
        "系统限制：Android 11+ 无法读取 Android/data、Android/obb 目录，请把漫画移到其他文件夹。"
    } else {
        "无法访问「${dir.name}」：权限不足或被系统限制。"
    }
