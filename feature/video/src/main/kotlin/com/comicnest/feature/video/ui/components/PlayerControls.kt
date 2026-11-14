package com.comicnest.feature.video.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comicnest.core.common.util.formatDuration
import com.comicnest.core.ui.components.FullNameDialog

/**
 * 播放页控制层（DESIGN v2 §9.5 / §11.4 R4 / §12）：顶栏返回 + 文件名；底部三层：
 * A 左上时间「8:21 / 23:05」；B 惯用风格细进度条；C 左下播放/暂停 + 下一个视频，
 * 右下选集（仅横屏）+ 倍速 + 旋转。只渲染状态与上报事件，不含业务判断。
 */
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    ended: Boolean,
    hasNext: Boolean,
    episodeButtonVisible: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleOrientation: () -> Unit,
    onPlayNext: () -> Unit,
    onEpisodeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            // A 层：左上角时间标记
            Text(
                text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
            // B 层：细轨道 + 小光标进度条
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                enabled = durationMs > 0L,
                onSeekTo = onSeekTo,
            )
            // C 层：左侧播放/下一个，右侧倍速/旋转
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                        if (ended) {
                            Icon(
                                Icons.Filled.Replay,
                                contentDescription = "重播",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onPlayNext,
                        enabled = hasNext,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "下一个视频",
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 「选集」仅横屏可见（DESIGN v2 §12）
                    if (episodeButtonVisible) {
                        TextButton(onClick = onEpisodeClick) {
                            Text("选集", color = Color.White)
                        }
                    }
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
        }
    }
}

/**
 * 惯用风格进度条：轨道 3dp（拖动 4.5dp）、光标 5dp 半径（拖动 7dp），
 * 触控命中区 28dp；拖动期间只更新草稿，抬手才 seek。
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftFraction by remember { mutableStateOf<Float?>(null) }
    val dragging = draftFraction != null
    // 手势闭包在 pointerInput 中长寿，用最新值避免拖动结束后仍用旧时长
    val latestDuration by rememberUpdatedState(durationMs)
    val latestSeekTo by rememberUpdatedState(onSeekTo)
    val fraction = draftFraction
        ?: if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun seekToFraction(f: Float) {
        if (latestDuration > 0L) latestSeekTo((latestDuration * f).toLong())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SEEKBAR_TOUCH_HEIGHT)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    seekToFraction((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        draftFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        draftFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        draftFraction?.let(::seekToFraction)
                        draftFraction = null
                    },
                    onDragCancel = { draftFraction = null },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeight = if (dragging) TRACK_HEIGHT_DRAGGING.toPx() else TRACK_HEIGHT.toPx()
            val radius = trackHeight / 2f
            val knobRadius = if (dragging) KNOB_RADIUS_DRAGGING.toPx() else KNOB_RADIUS.toPx()
            val knobX = (size.width * fraction).coerceIn(knobRadius, size.width - knobRadius)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius),
            )
            if (knobX > radius) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(knobX, trackHeight),
                    cornerRadius = CornerRadius(radius),
                )
            }
            drawCircle(
                color = Color.White,
                radius = knobRadius,
                center = Offset(knobX, centerY),
            )
        }
    }
}

private val SEEKBAR_TOUCH_HEIGHT = 28.dp
private val TRACK_HEIGHT = 3.dp
private val TRACK_HEIGHT_DRAGGING = 4.5.dp
private val KNOB_RADIUS = 5.dp
private val KNOB_RADIUS_DRAGGING = 7.dp

private fun speedLabel(speed: Float): String = when (speed) {
    1f -> "1.0x"
    2f -> "2.0x"
    else -> "${speed}x"
}
