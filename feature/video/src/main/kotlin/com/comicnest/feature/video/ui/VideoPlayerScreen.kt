package com.comicnest.feature.video.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.comicnest.core.common.util.formatDuration
import com.comicnest.core.model.VideoOrientation
import com.comicnest.feature.video.ui.components.EpisodeDrawer
import com.comicnest.feature.video.ui.components.PlayerControls
import com.comicnest.feature.video.viewmodel.VideoPlayerUiState
import com.comicnest.feature.video.viewmodel.VideoPlayerViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 播放页（DESIGN v2 §9.5）：
 * - 全屏黑底 + ExoPlayer `PlayerView`（原生控件关闭，控制层自绘）。
 * - 单击显隐控件、双击暂停/播放、长按任意位置临时加速（左右不限，松手恢复）、
 *   左右滑快退快进、左半屏上下滑亮度、右半屏上下滑音量。
 * - 进入隐藏系统栏、屏幕常亮；退出恢复系统栏、亮度与屏幕方向。
 */
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val orientationRequest by viewModel.orientationRequest.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    // 「选集」入口仅横屏提供（DESIGN v2 §12）
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by remember { mutableStateOf(true) }
    var hintText by remember { mutableStateOf<String?>(null) }
    var episodeDrawerOpen by rememberSaveable { mutableStateOf(false) }
    // 已应用方向的视频 key：同一视频的 config change 不重复应用，不覆盖用户手动旋转；
    // 切换「下一个视频」时 key 变化，重新应用（DESIGN v2 §11.6 R6）
    var orientedKeyId by rememberSaveable { mutableStateOf<String?>(null) }
    // 持有 PlayerView 引用：退出时主动清屏，避免导航淡出期间残留最后一帧
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val dismissPlayer: () -> Unit = {
        viewModel.onStop()
        // 立即释放播放器，尽早归还文件句柄（DESIGN v2 §11.2 R2）
        viewModel.releasePlayer()
        playerView?.apply {
            player = null
            visibility = View.INVISIBLE
        }
        onBack()
    }

    BackHandler {
        if (episodeDrawerOpen) {
            episodeDrawerOpen = false
        } else {
            dismissPlayer()
        }
    }

    // 退到后台：停播并强制落库一次（DESIGN v2 §3.8）
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 屏幕常亮
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 沉浸式：进入隐藏系统栏，退出恢复并复位方向与亮度
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = attrs
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 默认视频朝向（DESIGN v2 §11.6 R6）：装载阶段已算好，进入新视频时应用
    LaunchedEffect(orientationRequest) {
        val request = orientationRequest ?: return@LaunchedEffect
        if (request.keyId == orientedKeyId) return@LaunchedEffect
        activity?.requestedOrientation = when (request.orientation) {
            VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        orientedKeyId = request.keyId
    }

    // 竖屏不提供选集入口：旋回竖屏时自动收起
    LaunchedEffect(isLandscape) {
        if (!isLandscape) episodeDrawerOpen = false
    }

    // 播放中 3s 后自动隐藏控件（选集抽屉打开时保持可见）
    LaunchedEffect(controlsVisible, playback.isPlaying, episodeDrawerOpen) {
        if (controlsVisible && playback.isPlaying && !episodeDrawerOpen) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val state = uiState) {
            VideoPlayerUiState.Loading -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )

            is VideoPlayerUiState.Error -> PlayerError(
                message = state.message,
                onBack = dismissPlayer,
                modifier = Modifier.align(Alignment.Center),
            )

            is VideoPlayerUiState.Ready -> {
                // 顶栏展示完整文件名（含扩展名），过长时由控制层省略并支持点击查看全名
                val title = remember(state.summary.key) {
                    state.summary.key.fileName
                }
                val latestDuration by rememberUpdatedState(playback.durationMs)
                val latestPosition by rememberUpdatedState(playback.positionMs)
                val audioManager = remember(context) {
                    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            player = viewModel.player
                            playerView = this
                        }
                    },
                    update = { it.player = viewModel.player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )

                // 手势层：单击显隐 / 双击暂停 / 长按临时加速 / 拖动（seek、亮度、音量）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    // 按住到抬手/取消的全过程；松手时结束临时加速（未加速时幂等）
                                    tryAwaitRelease()
                                    viewModel.onBoostEnd()
                                },
                                onLongPress = { viewModel.onBoostStart() },
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = {
                                    viewModel.onTogglePlay()
                                    controlsVisible = true
                                },
                            )
                        }
                        .pointerInput(audioManager) {
                            var mode = DragMode.NONE
                            var startX = 0f
                            var drag = Offset.Zero
                            var startPosition = 0L
                            var startBrightness = 0.5f
                            var startVolume = 0
                            val slop = DRAG_SLOP_DP.dp.toPx()
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // 长按加速后转为滑动：先退出加速，交给拖动处理
                                    viewModel.onBoostEnd()
                                    mode = DragMode.NONE
                                    drag = Offset.Zero
                                    startX = offset.x
                                    startPosition = latestPosition
                                    startVolume = audioManager
                                        .getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val current = activity?.window?.attributes?.screenBrightness
                                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                    startBrightness = if (current < 0f) 0.5f else current
                                },
                                onDragCancel = { hintText = null },
                                onDragEnd = {
                                    if (mode == DragMode.SEEK && latestDuration > 0L) {
                                        val ratio = (drag.x / size.width).coerceIn(-1f, 1f)
                                        val delta = (latestDuration * ratio)
                                            .toLong()
                                            .coerceIn(-SEEK_MAX_JUMP_MS, SEEK_MAX_JUMP_MS)
                                        viewModel.onSeekTo(startPosition + delta)
                                    }
                                    hintText = null
                                    mode = DragMode.NONE
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    drag += dragAmount
                                    if (mode == DragMode.NONE) {
                                        mode = when {
                                            abs(drag.x) > slop && abs(drag.x) > abs(drag.y) ->
                                                DragMode.SEEK
                                            abs(drag.y) > slop && startX < size.width / 2f ->
                                                DragMode.BRIGHTNESS
                                            abs(drag.y) > slop -> DragMode.VOLUME
                                            else -> DragMode.NONE
                                        }
                                    }
                                    when (mode) {
                                        DragMode.SEEK -> {
                                            if (latestDuration > 0L) {
                                                val ratio =
                                                    (drag.x / size.width).coerceIn(-1f, 1f)
                                                val delta = (latestDuration * ratio)
                                                    .toLong()
                                                    .coerceIn(-SEEK_MAX_JUMP_MS, SEEK_MAX_JUMP_MS)
                                                val target = (startPosition + delta)
                                                    .coerceIn(0L, latestDuration)
                                                hintText =
                                                    "${formatDuration(target)} / " +
                                                        formatDuration(latestDuration)
                                            }
                                        }

                                        DragMode.BRIGHTNESS -> {
                                            val next = (startBrightness - drag.y / size.height)
                                                .coerceIn(0.05f, 1f)
                                            activity?.window?.let { window ->
                                                val attrs = window.attributes
                                                attrs.screenBrightness = next
                                                window.attributes = attrs
                                            }
                                            hintText = "亮度 ${(next * 100).roundToInt()}%"
                                        }

                                        DragMode.VOLUME -> {
                                            val maxVolume = audioManager
                                                .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                .coerceAtLeast(1)
                                            val next = (startVolume -
                                                drag.y / size.height * maxVolume * 1.5f)
                                                .roundToInt()
                                                .coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                next,
                                                0,
                                            )
                                            hintText =
                                                "音量 ${next * 100 / maxVolume}%"
                                        }

                                        DragMode.NONE -> Unit
                                    }
                                },
                            )
                        },
                )

                hintText?.let { text ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(text = text, color = Color.White, fontSize = 15.sp)
                    }
                }

                // 长按临时加速提示：左上角淡色小字，不遮挡画面
                playback.boostSpeed?.let { boost ->
                    Text(
                        text = "${boost.toInt()}X",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .padding(start = 16.dp, top = 56.dp),
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PlayerControls(
                        title = title,
                        isPlaying = playback.isPlaying,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        speed = playback.speed,
                        ended = playback.ended,
                        hasNext = hasNext,
                        episodeButtonVisible = isLandscape,
                        onBack = dismissPlayer,
                        onTogglePlay = {
                            viewModel.onTogglePlay()
                            controlsVisible = true
                        },
                        onSeekTo = viewModel::onSeekTo,
                        onCycleSpeed = viewModel::onCycleSpeed,
                        onToggleOrientation = { toggleOrientation(activity) },
                        onPlayNext = {
                            viewModel.onPlayNext()
                            controlsVisible = true
                        },
                        onEpisodeClick = { episodeDrawerOpen = true },
                    )
                }

                // 选集抽屉：置于控制层之上，点遮罩关闭
                EpisodeDrawer(
                    visible = episodeDrawerOpen,
                    playlist = playlist,
                    onSelect = { summary ->
                        episodeDrawerOpen = false
                        controlsVisible = true
                        viewModel.onPlayVideo(summary.key)
                    },
                    onThumbnailVisible = viewModel::onPlaylistThumbnailVisible,
                    onDismiss = { episodeDrawerOpen = false },
                )
            }
        }
    }
}

@Composable
private fun PlayerError(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("返回", color = Color.White)
        }
    }
}

/** 在横屏与竖屏之间切换（保持传感器方向，旋转不重建页面）。 */
private fun toggleOrientation(activity: Activity?) {
    if (activity == null) return
    val landscape =
        activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    activity.requestedOrientation = if (landscape) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val DRAG_SLOP_DP = 12
private const val SEEK_MAX_JUMP_MS = 300_000L
