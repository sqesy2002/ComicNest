package com.comicnest.feature.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comicnest.core.model.VideoRoot
import com.comicnest.feature.video.viewmodel.VIDEO_GROUP_SIZE
import kotlin.math.ceil

/**
 * 视频页左侧抽屉（DESIGN v2 §9.4）：搜索框（按文件名模糊过滤）+ 每 [VIDEO_GROUP_SIZE] 个一组的分组跳转
 * + 视频库目录概览。分组按全库总数划分，与搜索无关。
 */
@Composable
fun VideoDrawer(
    query: String,
    onQueryChange: (String) -> Unit,
    totalVideos: Int,
    currentGroup: Int,
    onGroupSelect: (Int) -> Unit,
    roots: List<VideoRoot>,
    onAddRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索视频文件名") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (totalVideos > VIDEO_GROUP_SIZE) {
            val groupCount = ceil(totalVideos / VIDEO_GROUP_SIZE.toFloat()).toInt()
            Text(
                text = "分组跳转 · 共 $totalVideos 个",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            (0 until groupCount).forEach { i ->
                val from = i * VIDEO_GROUP_SIZE + 1
                val to = minOf((i + 1) * VIDEO_GROUP_SIZE, totalVideos)
                GroupRow(
                    label = "第${i + 1}组   $from–$to",
                    selected = i == currentGroup,
                    onClick = { onGroupSelect(i) },
                )
            }
        } else {
            Text(
                text = if (totalVideos == 0) "视频库为空" else "视频不足 $VIDEO_GROUP_SIZE 个，无需分组",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "视频库目录",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (roots.isEmpty()) {
            Text(
                text = "尚未添加视频目录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Text(
                text = roots.joinToString("、") { it.displayName },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        TextButton(onClick = onAddRoot, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("添加视频目录", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun GroupRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Menu,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
