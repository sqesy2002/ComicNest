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
import com.comicnest.core.common.util.matchesAllKeywords
import com.comicnest.core.common.util.parseSearchKeywords
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.domain.usecase.video.EnsureVideoThumbnailUseCase
import com.comicnest.core.domain.usecase.video.GetVideoLibraryUseCase
import com.comicnest.core.domain.usecase.video.SaveVideoProgressUseCase
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoOrientation
import com.comicnest.core.model.VideoOrientationMode
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import com.comicnest.core.model.resolveVideoOrientation
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 播放页装载状态。 */
sealed interface VideoPlayerUiState {
    data object Loading : VideoPlayerUiState
    data class Ready(
        val summary: VideoSummary,
        val startPositionMs: Long,
    ) : VideoPlayerUiState

    data class Error(val message: String) : VideoPlayerUiState
}

/**
 * 进入/切换视频时请求的显示方向。
 * [keyId] 用于区分是否已应用：同一视频的 config change 不重复应用（保留用户手动旋转），
 * 切换视频则重新应用（DESIGN v2 §11.6 R6）。
 */
data class VideoOrientationRequest(
    val keyId: String,
    val orientation: VideoOrientation,
)

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
 * 选集抽屉数据（DESIGN v2 §12）：当前搜索词 + 排序下的全部视频 + 当前视频下标，
 * -1 表示当前视频不在列表中（如随机播放命中搜索范围外的视频）。
 */
data class PlayerPlaylist(
    val items: List<VideoSummary>,
    val currentIndex: Int,
)

/**
 * 播放器状态与进度编排（DESIGN v2 §3.8 / §11.4 / §11.6）：
 * - `ExoPlayer` 在 VM 内创建，退出页面时 `releasePlayer()` 立即释放，`onCleared` 兜底。
 * - 进度每 5s 节流落库；`onStop`/`onCleared` 强制写一次（≥10s 阈值在 UseCase 内）。
 * - 播放结束停在末帧，不自动连播；「下一个视频」与「选集」均按当前搜索词与排序的列表顺序切换。
 * - 进入时按偏好（竖屏/横屏/根据比例）确定方向，AUTO 仅在装载阶段读一次宽高。
 * - 选集抽屉数据（当前搜索词 + 排序下的全部视频，与视频页列表同源同序）随库 Flow 持续刷新，
 *   缩略图按需生成。
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val getVideoLibrary: GetVideoLibraryUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val saveProgress: SaveVideoProgressUseCase,
    private val ensureThumbnail: EnsureVideoThumbnailUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val initialKey: VideoKey? =
        savedStateHandle.get<String>(ARG_VIDEO_KEY_ID)?.let(VideoKey::fromId)

    /** 来自视频列表的路由参数：当前搜索词（空表示全库），用于复算「上一个/下一个」队列。 */
    private val query: String = savedStateHandle.get<String>(ARG_QUERY).orEmpty()

    private var currentKey: VideoKey? = null

    /** 当前搜索词 + 排序下的完整列表；由 [observeQueue] 随库变化持续刷新。 */
    private var fullQueue: List<VideoSummary> = emptyList()

    /** 未经搜索过滤的全库列表（随机播放等场景下当前视频不在搜索结果内时，选集回退到全库定位）。 */
    private var allVideos: List<VideoSummary> = emptyList()

    private val keywords: List<String> = parseSearchKeywords(query)

    /** 是否已释放播放器（释放后禁止再读写 player，避免异常）。 */
    private var released = false

    private val _uiState = MutableStateFlow<VideoPlayerUiState>(VideoPlayerUiState.Loading)
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    /** 队列中是否还有下一个可播视频（末个或后续文件均缺失时置灰）。 */
    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    /** 当前视频应使用的方向请求；null 表示尚未确定（装载阶段）。 */
    private val _orientationRequest = MutableStateFlow<VideoOrientationRequest?>(null)
    val orientationRequest: StateFlow<VideoOrientationRequest?> = _orientationRequest.asStateFlow()

    /** 选集抽屉：当前搜索词 + 排序下的全部视频。 */
    private val _playlist = MutableStateFlow(PlayerPlaylist(emptyList(), -1))
    val playlist: StateFlow<PlayerPlaylist> = _playlist.asStateFlow()

    /** 选集缩略图队列：最多 3 个并发，已入队的不重复请求。 */
    private val thumbGate = Semaphore(THUMB_CONCURRENCY)
    private val thumbRequested = mutableSetOf<String>()

    /** 供 `AndroidView(PlayerView)` 绑定；页面退出时通过 [releasePlayer] 立即释放。 */
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
        observeQueue()
        val key = initialKey
        if (key == null) {
            _uiState.value = VideoPlayerUiState.Error("视频参数无效")
        } else {
            open(key)
        }
    }

    /** 装载并播放指定视频（首次进入与「下一个视频」共用）。 */
    private fun open(key: VideoKey) {
        viewModelScope.launch {
            val summary = videoRepository.getVideo(key).getOrElse { t ->
                _uiState.value = VideoPlayerUiState.Error(t.message ?: "无法打开视频")
                return@launch
            }
            // 先确定方向再进入 Ready，避免「先竖后横」的闪动；切换视频时会再次下发请求
            _orientationRequest.value =
                VideoOrientationRequest(key.id, resolveEntryOrientation(key))
            val start = videoRepository.getProgress(key)
                ?.positionMs
                ?.takeIf { it > 0L && it < (summary.durationMs ?: Long.MAX_VALUE) }
                ?: 0L
            _uiState.value = VideoPlayerUiState.Ready(summary, start)
            currentKey = key
            lastSavedPosition = -1L
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(summary.absPath))))
            player.prepare()
            if (start > 0L) player.seekTo(start)
            player.playWhenReady = true
            refreshPlaylist()
            _hasNext.value = pickNext(key) != null
        }
    }

    /** 读取偏好并解析进入方向；AUTO 时才按需读一次视频宽高。 */
    private suspend fun resolveEntryOrientation(key: VideoKey): VideoOrientation {
        val mode = preferencesRepository.observeVideoOrientation().first()
        val dimensions = if (mode == VideoOrientationMode.AUTO) {
            videoRepository.readDimensions(key).getOrNull()
        } else {
            null
        }
        return resolveVideoOrientation(mode, dimensions)
    }

    /** 持续跟踪视频库（缩略图回写、删除、重命名、排序变化），同步选集窗口与「下一个」可用性。 */
    private fun observeQueue() {
        viewModelScope.launch {
            getVideoLibrary()
                .catch { t -> AppLog.e("VideoPlayer", "读取视频队列失败", t) }
                .collect { data ->
                    allVideos = data.videos
                    fullQueue = data.videos.filter {
                        matchesAllKeywords(it.key.fileName, keywords)
                    }
                    refreshPlaylist()
                    _hasNext.value = pickNext(currentKey) != null
                }
        }
    }

    private fun refreshPlaylist() {
        val key = currentKey
        // 当前视频不在搜索结果内（如随机播放命中库外视频）时回退到全库，保证能定位到当前项
        val notInQueue = key != null && fullQueue.none { it.key == key }
        val source = if (notInQueue && allVideos.isNotEmpty()) allVideos else fullQueue
        _playlist.value = buildPlaylist(source, key)
    }

    /** 队列中当前视频之后的第一个仍存在的文件（最多跳过 [MAX_SKIP_IN_QUEUE] 个失效项）。 */
    private fun pickNext(current: VideoKey?): VideoSummary? =
        nextVideoAfter(fullQueue, current) { File(it.absPath).isFile }

    /** 「下一个视频」：先落库当前进度，再在同一 VM 内切换（不重建页面）。 */
    fun onPlayNext() {
        if (released) return
        val next = pickNext(currentKey) ?: return
        switchTo(next.key)
    }

    /** 选集抽屉选择视频：当前项不响应，其余同样在页内切换。 */
    fun onPlayVideo(key: VideoKey) {
        if (released || key == currentKey) return
        switchTo(key)
    }

    /** 选集可见项触发缩略图（与网格共用 UseCase 去重，最多 [THUMB_CONCURRENCY] 并发）。 */
    fun onPlaylistThumbnailVisible(summary: VideoSummary) {
        if (summary.thumbState != VideoThumbnailState.PENDING) return
        if (!thumbRequested.add(summary.key.id)) return
        viewModelScope.launch {
            thumbGate.withPermit {
                runCatching { ensureThumbnail(summary.key) }
            }
        }
    }

    /** 页内切换视频：先落库当前进度、退出临时加速，再装载新视频。 */
    private fun switchTo(key: VideoKey) {
        onBoostEnd()
        persistProgress(force = true)
        player.stop()
        _playback.update { it.copy(ended = false, boostSpeed = null) }
        open(key)
    }

    /** 播放中每 500ms 同步一次位置，并按 5s 窗口节流落库。 */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                if (!released && player.isPlaying) {
                    syncPosition()
                    persistProgress(force = false)
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun syncPosition() {
        if (released) return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        _playback.update { it.copy(positionMs = position, durationMs = duration) }
    }

    fun onTogglePlay() {
        if (released) return
        if (_playback.value.ended) {
            player.seekTo(0L)
            _playback.update { it.copy(ended = false) }
            player.play()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun onSeekTo(positionMs: Long) {
        if (released) return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val target = positionMs.coerceIn(0L, duration)
        player.seekTo(target)
        _playback.update { it.copy(positionMs = target, ended = false) }
    }

    /** 倍速循环切换 0.5 → 1.0 → 1.5 → 2.0（DESIGN v2 §9.5）。 */
    fun onCycleSpeed() {
        if (released) return
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
        if (released) return
        if (_playback.value.boostSpeed != null) return
        if (_playback.value.ended || !player.isPlaying) return
        val target = boostSpeedFor(_playback.value.speed)
        player.setPlaybackSpeed(target)
        _playback.update { it.copy(boostSpeed = target) }
    }

    /** 结束临时加速并恢复用户设定倍速（幂等）。 */
    fun onBoostEnd() {
        if (_playback.value.boostSpeed == null) return
        if (!released) player.setPlaybackSpeed(_playback.value.speed)
        _playback.update { it.copy(boostSpeed = null) }
    }

    /** 页面退出前调用（DisposableEffect/返回键），强制落库一次。 */
    fun onStop() {
        if (released) return
        onBoostEnd()
        persistProgress(force = true)
        player.pause()
    }

    /**
     * 立即释放播放器：退出页面时先解除与 `PlayerView` 的绑定再调用，尽早释放文件句柄，
     * 避免随后在列表页删除同一文件时被自身占用（DESIGN v2 §11.2 R2）。幂等。
     */
    fun releasePlayer() {
        if (released) return
        released = true
        ticker?.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun persistProgress(force: Boolean) {
        if (released) return
        val safeKey = currentKey ?: return
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
        if (released) return
        val safeKey = currentKey ?: return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val position = player.currentPosition.coerceAtLeast(0L)
        persistScope.launch { saveProgress(safeKey, position, duration) }
    }

    override fun onCleared() {
        persistProgressFinal()
        releasePlayer()
        super.onCleared()
    }

    companion object {
        const val ARG_VIDEO_KEY_ID = "videoKeyId"
        const val ARG_QUERY = "query"
        private const val POSITION_TICK_MS = 500L
        const val SAVE_INTERVAL_MS = 5_000L
    }
}

/**
 * 队列中 [current] 之后第一个可播放的视频（最多跳过 [MAX_SKIP_IN_QUEUE] 个失效项，DESIGN v2 §11.4 R4）。
 * 提取为纯函数以便单测；[isPlayable] 由调用方给出（VM 用文件存在性判断）。
 */
internal fun nextVideoAfter(
    queue: List<VideoSummary>,
    current: VideoKey?,
    isPlayable: (VideoSummary) -> Boolean,
): VideoSummary? {
    val key = current ?: return null
    val index = queue.indexOfFirst { it.key == key }
    if (index < 0) return null
    var cursor = index + 1
    var skipped = 0
    while (cursor < queue.size && skipped < MAX_SKIP_IN_QUEUE) {
        val candidate = queue[cursor]
        if (isPlayable(candidate)) return candidate
        cursor++
        skipped++
    }
    return null
}

/** 「下一个视频」最多跳过的失效文件数。 */
private const val MAX_SKIP_IN_QUEUE = 3

/** 选集缩略图并发数（与列表页一致）。 */
private const val THUMB_CONCURRENCY = 3

/**
 * 构建选集列表（DESIGN v2 §12）：完整保留传入的列表（与视频页同源同序），
 * 只额外定位当前视频下标；不在列表内时 [PlayerPlaylist.currentIndex] 为 -1。
 */
internal fun buildPlaylist(
    queue: List<VideoSummary>,
    current: VideoKey?,
): PlayerPlaylist {
    val index = current?.let { key -> queue.indexOfFirst { it.key == key } } ?: -1
    return PlayerPlaylist(items = queue, currentIndex = index)
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
