package com.comicnest.feature.video.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comicnest.core.model.VideoSummary

/** 长按动作菜单（DESIGN v2 §9.6）：播放 / 重命名 / 删除（视频无收藏动作）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
    target: VideoSummary,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            ListItem(
                headlineContent = { Text(target.displayName) },
                supportingContent = { Text(target.key.fileName) },
            )
            HorizontalDivider()
            ActionRow(
                label = "播放",
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = onPlay,
            )
            ActionRow(
                label = "重命名",
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = onRename,
            )
            ActionRow(
                label = "删除",
                icon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** 删除二次确认（DESIGN v2 §3.7 第 1 步）：文案明确“不可恢复”。 */
@Composable
fun VideoDeleteDialog(
    target: VideoSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除视频") },
        text = {
            Column {
                Text("确定删除「${target.key.fileName}」？")
                Text(
                    text = "删除后不可恢复，文件将从设备中永久移除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
