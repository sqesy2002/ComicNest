package com.comicnest.feature.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import com.comicnest.feature.video.viewmodel.PlayerPlaylist
import java.io.File

/** 当前正在播放的视频名称颜色（DESIGN v2 §12）。 */
private val CurrentEpisodeColor = Color(0xFF5AA9FF)

/**
 * 播放页「选集」抽屉（DESIGN v2 §12）：从右侧滑入，点遮罩关闭；
 * 单列展示当前搜索词 + 排序下的全部视频（缩略图 + 名称），与视频页列表同源同序；
 * 打开时自动滚到当前项，当前项名称蓝色且不可点击。
 */
@Composable
fun EpisodeDrawer(
    visible: Boolean,
    playlist: PlayerPlaylist,
    onSelect: (VideoSummary) -> Unit,
    onThumbnailVisible: (VideoSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                color = EPISODE_PANEL_COLOR,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(EPISODE_PANEL_WIDTH)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    // 消费面板空白处的点击，避免穿透到遮罩导致抽屉被误关
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "选集",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${playlist.items.size} 个",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    EpisodeList(
                        visible = visible,
                        playlist = playlist,
                        onSelect = onSelect,
                        onThumbnailVisible = onThumbnailVisible,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    visible: Boolean,
    playlist: PlayerPlaylist,
    onSelect: (VideoSummary) -> Unit,
    onThumbnailVisible: (VideoSummary) -> Unit,
) {
    val listState = rememberLazyListState()
    // 打开或切换当前视频后，把当前项滚到可见区域（留一项作为上方余量）
    LaunchedEffect(visible, playlist.currentIndex) {
        if (visible && playlist.currentIndex >= 0) {
            listState.scrollToItem((playlist.currentIndex - 1).coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(playlist.items, key = { _, item -> item.key.id }) { index, summary ->
            EpisodeRow(
                summary = summary,
                isCurrent = index == playlist.currentIndex,
                onClick = { onSelect(summary) },
                onThumbnailVisible = onThumbnailVisible,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    summary: VideoSummary,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onThumbnailVisible: (VideoSummary) -> Unit,
) {
    LaunchedEffect(summary.key.id, summary.thumbState) {
        if (summary.thumbState == VideoThumbnailState.PENDING) onThumbnailVisible(summary)
    }
    val thumbPath = summary.thumbPath
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                summary.thumbState == VideoThumbnailState.READY && thumbPath != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(thumbPath))
                            .crossfade(150)
                            .build(),
                        contentDescription = summary.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                summary.thumbState == VideoThumbnailState.FAILED -> {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        strokeWidth = 1.5.dp,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = summary.displayName,
            color = if (isCurrent) CurrentEpisodeColor else Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val EPISODE_PANEL_COLOR = Color(0xFF16181C)
private val EPISODE_PANEL_WIDTH = 300.dp
private val THUMB_WIDTH = 96.dp
private val THUMB_HEIGHT = 54.dp