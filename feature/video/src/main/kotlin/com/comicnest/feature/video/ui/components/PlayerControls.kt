package com.comicnest.feature.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comicnest.core.common.util.formatDuration
import com.comicnest.core.ui.components.FullNameDialog

/**
 * 播放页控制层（DESIGN v2 §9.5）：顶栏返回 + 文件名；底部进度条 + 时间 + ±15s + 播放/暂停 + 倍速 + 方向。
 * 只渲染状态与上报事件，不含业务判断。
 */
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    ended: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    // 拖动时只更新草稿，抬手才真正 seek，避免高频 seek 卡顿
    var sliderDraft by remember { mutableStateOf<Float?>(null) }
    // 点击顶栏标题：弹窗展示完整文件名（可滚动）并支持一键复制
    var showFullTitle by remember { mutableStateOf(false) }
    if (showFullTitle) {
        FullNameDialog(
            title = "视频文件名",
            name = title,
            onDismissRequest = { showFullTitle = false },
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        // 顶部渐变遮罩 + 返回/标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                )
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { showFullTitle = true }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                )
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Slider(
                value = sliderDraft ?: positionMs.coerceIn(0L, safeDuration).toFloat(),
                onValueChange = { sliderDraft = it },
                onValueChangeFinished = {
                    val target = sliderDraft
                    sliderDraft = null
                    if (target != null) onSeekTo(target.toLong())
                },
                valueRange = 0f..safeDuration.toFloat(),
                enabled = durationMs > 0L,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSeekBy(-SEEK_STEP_MS) }) {
                        Text("-15s", color = Color.White)
                    }
                    IconButton(onClick = onTogglePlay) {
                        if (ended) {
                            Icon(Icons.Filled.Replay, contentDescription = "重播", tint = Color.White)
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                            )
                        }
                    }
                    TextButton(onClick = { onSeekBy(SEEK_STEP_MS) }) {
                        Text("+15s", color = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onCycleSpeed) {
                        Text(speedLabel(speed), color = Color.White)
                    }
                    IconButton(onClick = onToggleOrientation) {
                        Icon(
                            Icons.Filled.ScreenRotation,
                            contentDescription = "切换横竖屏",
                            tint = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

private const val SEEK_STEP_MS = 15_000L

private fun speedLabel(speed: Float): String = when (speed) {
    1f -> "1.0x"
    2f -> "2.0x"
    else -> "${speed}x"
}
