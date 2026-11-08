package com.comicnest.feature.video.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.comicnest.core.common.log.AppLog
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.domain.usecase.video.SaveVideoProgressUseCase
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 播放页装载状态。 */
sealed interface VideoPlayerUiState {
    data object Loading : VideoPlayerUiState
    data class Ready(
        val summary: VideoSummary,
        val startPositionMs: Long,
    ) : VideoPlayerUiState

    data class Error(val message: String) : VideoPlayerUiState
}

/** 播放控制面（供 UI 渲染，不含窗口/音量等系统能力）。 */
data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val ended: Boolean = false,
    /** 长按画面临时加速倍率；null 表示未加速（不改变 [speed] 用户设定）。 */
    val boostSpeed: Float? = null,
)

/**
 * 播放器状态与进度编排（DESIGN v2 §3.8）：
 * - `ExoPlayer` 在 VM 内创建，`onCleared` 释放；音频焦点交给 Media3 处理。
 * - 进度每 5s 节流落库；`onStop`/`onCleared` 强制写一次（≥10s 阈值在 UseCase 内）。
 * - 播放结束停在末帧，不自动连播。
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val saveProgress: SaveVideoProgressUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val key: VideoKey? = savedStateHandle.get<String>(ARG_VIDEO_KEY_ID)?.let(VideoKey::fromId)

    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    /** 供 `AndroidView(PlayerView)` 绑定；页面销毁时随 VM 一起释放。 */
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playback.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> syncPosition()
                Player.STATE_ENDED -> {
                    // 播放结束：退出临时加速并恢复用户设定倍速
                    onBoostEnd()
                    _playback.update { it.copy(ended = true, isPlaying = false) }
                    persistProgress(force = true)
                }
            }
        }

        /** 编解码不支持/文件损坏时切到错误态，避免停留在黑屏。 */
        override fun onPlayerError(error: PlaybackException) {
            AppLog.e("VideoPlayer", "播放失败：${error.errorCodeName}", error)
            _playback.update { it.copy(isPlaying = false) }
            _uiState.value = VideoPlayerUiState.Error("无法播放该视频（${error.errorCodeName}）")
        }
    }

    private var ticker: Job? = null

    /** onCleared 时 viewModelScope 已取消，用一个独立单线程 scope 保序完成写库。 */
    private val persistScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private var lastSavedPosition = -1L

    init {
        player.addListener(listener)
        startTicker()
        viewModelScope.launch {
            val safeKey = key ?: run {
                _uiState.value = VideoPlayerUiState.Error("视频参数无效")
                return@launch
            }
            videoRepository.getVideo(safeKey).fold(
                onSuccess = { summary ->
                    val start = videoRepository.getProgress(safeKey)
                        ?.positionMs
                        ?.takeIf { it > 0L && it < (summary.durationMs ?: Long.MAX_VALUE) }
                        ?: 0L
                    _uiState.value = VideoPlayerUiState.Ready(summary, start)
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(summary.absPath))))
                    player.prepare()
                    if (start > 0L) player.seekTo(start)
                    player.playWhenReady = true
                },
                onFailure = { t ->
                    _uiState.value = VideoPlayerUiState.Error(t.message ?: "无法打开视频")
                },
            )
        }
    }

    /** 播放中每 500ms 同步一次位置，并按 5s 窗口节流落库。 */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    syncPosition()
                    persistProgress(force = false)
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun syncPosition() {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        _playback.update { it.copy(positionMs = position, durationMs = duration) }
    }

    fun onTogglePlay() {
        if (_playback.value.ended) {
            player.seekTo(0L)
            _playback.update { it.copy(ended = false) }
            player.play()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun onSeekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        player.playWhenReady = true
        _playback.update { it.copy(ended = false, positionMs = target) }
    }

    fun onSeekTo(positionMs: Long) {
        val duration = player.duration.takeIf { it > 0L } ?: return
        val target = positionMs.coerceIn(0L, duration)
        player.seekTo(target)
        _playback.update { it.copy(positionMs = target, ended = false) }
    }

    /** 倍速循环切换 0.5 → 1.0 → 1.5 → 2.0（DESIGN v2 §9.5）。 */
    fun onCycleSpeed() {
        val next = when (_playback.value.speed) {
            0.5f -> 1f
            1f -> 1.5f
            1.5f -> 2f
            else -> 0.5f
        }
        val boosting = _playback.value.boostSpeed != null
        _playback.update { it.copy(speed = next) }
        // 长按加速中不覆盖临时倍率，松手时统一恢复为新设定
        if (!boosting) player.setPlaybackSpeed(next)
    }

    /**
     * 长按画面开始临时加速（左右不限，均正向加速）：不影响用户设定的 [PlaybackUiState.speed]，
     * 松手/拖动/播放结束时由 [onBoostEnd] 恢复。暂停状态下长按不生效。
     */
    fun onBoostStart() {
        if (_playback.value.boostSpeed != null) return
        if (_playback.value.ended || !player.isPlaying) return
        val target = boostSpeedFor(_playback.value.speed)
        player.setPlaybackSpeed(target)
        _playback.update { it.copy(boostSpeed = target) }
    }

    /** 结束临时加速并恢复用户设定倍速（幂等）。 */
    fun onBoostEnd() {
        if (_playback.value.boostSpeed == null) return
        player.setPlaybackSpeed(_playback.value.speed)
        _playback.update { it.copy(boostSpeed = null) }
    }

    /** 页面退出前调用（DisposableEffect/返回键），强制落库一次。 */
    fun onStop() {
        onBoostEnd()
        persistProgress(force = true)
        player.pause()
    }

    private fun persistProgress(force: Boolean) {
        val safeKey = key ?: return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        if (!force) {
            if (position - lastSavedPosition < SAVE_INTERVAL_MS) return
        }
        lastSavedPosition = position
        // ExoPlayer 只能在主线程读取，落库放到 IO；单线程保证后写覆盖前写
        persistScope.launch { saveProgress(safeKey, position, duration) }
    }

    /** 最后一次写库（onCleared 时 viewModelScope 已不可用）。 */
    private fun persistProgressFinal() {
        val safeKey = key ?: return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        persistScope.launch { saveProgress(safeKey, position, duration) }
    }

    override fun onCleared() {
        persistProgressFinal()
        ticker?.cancel()
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }

    companion object {
        const val ARG_VIDEO_KEY_ID = "videoKeyId"
        private const val POSITION_TICK_MS = 500L
        const val SAVE_INTERVAL_MS = 5_000L
    }
}

/**
 * 长按加速倍率：当前设定低于 2x 时用 2x，否则用 3x，保证长按一定比当前快。
 * 提取为纯函数以便单测。
 */
internal fun boostSpeedFor(currentSpeed: Float): Float =
    if (currentSpeed >= BOOST_HIGH_THRESHOLD) BOOST_HIGH_SPEED else BOOST_BASE_SPEED

private const val BOOST_HIGH_THRESHOLD = 2f
private const val BOOST_BASE_SPEED = 2f
private const val BOOST_HIGH_SPEED = 3f
