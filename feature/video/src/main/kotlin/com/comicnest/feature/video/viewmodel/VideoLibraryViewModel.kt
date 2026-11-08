package com.comicnest.feature.video.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.video.AddVideoRootUseCase
import com.comicnest.core.domain.usecase.video.DeleteVideoUseCase
import com.comicnest.core.domain.usecase.video.EnsureVideoThumbnailUseCase
import com.comicnest.core.domain.usecase.video.GetVideoLibraryUseCase
import com.comicnest.core.domain.usecase.video.RandomVideoUseCase
import com.comicnest.core.domain.usecase.video.RenameVideoUseCase
import com.comicnest.core.domain.usecase.video.ScanVideoLibraryUseCase
import com.comicnest.core.domain.usecase.video.RemoveVideoRootUseCase
import com.comicnest.core.model.RecentVideo
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 视频页 UI 状态（DESIGN v2 §3.8，与 v1 书架同构）。 */
sealed interface VideoLibraryUiState {
    data object Loading : VideoLibraryUiState
    data class Ready(
        val videos: List<VideoSummary>,
        val sortMode: VideoSortMode,
        val roots: List<VideoRoot>,
        val recent: List<RecentVideo>,
    ) : VideoLibraryUiState

    /** [firstRun] 为 true 表示尚未导入任何视频根（引导添加）。 */
    data class Empty(val firstRun: Boolean) : VideoLibraryUiState
    data class NoResult(val query: String) : VideoLibraryUiState
    data class Error(val message: String) : VideoLibraryUiState
}

/** 一次性事件（导航/提示），避免重放。 */
sealed interface VideoEvent {
    data class NavigateToPlayer(val videoKeyId: String) : VideoEvent
    data class Message(val text: String) : VideoEvent
}

/** 抽屉分组每组视频数（DESIGN v2 V3）。 */
const val VIDEO_GROUP_SIZE = 50

@HiltViewModel
class VideoLibraryViewModel @Inject constructor(
    private val getVideoLibrary: GetVideoLibraryUseCase,
    private val scanVideoLibrary: ScanVideoLibraryUseCase,
    private val addVideoRoot: AddVideoRootUseCase,
    private val removeVideoRoot: RemoveVideoRootUseCase,
    private val randomVideo: RandomVideoUseCase,
    private val deleteVideo: DeleteVideoUseCase,
    private val renameVideo: RenameVideoUseCase,
    private val ensureThumbnail: EnsureVideoThumbnailUseCase,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VideoLibraryUiState>(VideoLibraryUiState.Loading)
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    /** 搜索关键词（纯内存临时态，不持久化）。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 全库视频总数（不受搜索影响，供分组固定划分）。 */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 长按菜单目标。 */
    private val _actionTarget = MutableStateFlow<VideoSummary?>(null)
    val actionTarget: StateFlow<VideoSummary?> = _actionTarget.asStateFlow()

    /** 删除确认框目标。 */
    private val _pendingDelete = MutableStateFlow<VideoSummary?>(null)
    val pendingDelete: StateFlow<VideoSummary?> = _pendingDelete.asStateFlow()

    /** 重命名弹窗目标。 */
    private val _pendingRename = MutableStateFlow<VideoSummary?>(null)
    val pendingRename: StateFlow<VideoSummary?> = _pendingRename.asStateFlow()

    private val _events = Channel<VideoEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 缩略图生成队列：最多 3 个并发；已入队/已出图的 key 不重复排队。 */
    private val thumbGate = Semaphore(3)
    private val thumbRequested = mutableSetOf<String>()
    private var thumbFailureStreak = 0

    init {
        viewModelScope.launch {
            combine(getVideoLibrary(), _query) { data, q -> data to q }
                .collect { (data, q) -> render(data, q) }
        }
    }

    private fun render(
        data: com.comicnest.core.domain.model.VideoLibraryUiData,
        query: String,
    ) {
        // 手动刷新失败提示不被后续流立刻覆盖
        if (_uiState.value is VideoLibraryUiState.Error) return
        _totalCount.value = data.videos.size
        val keyword = query.trim()
        val filtered = if (keyword.isEmpty()) {
            data.videos
        } else {
            data.videos.filter { it.key.fileName.contains(keyword, ignoreCase = true) }
        }
        _uiState.value = when {
            data.roots.isEmpty() -> VideoLibraryUiState.Empty(firstRun = true)
            data.videos.isEmpty() -> VideoLibraryUiState.Empty(firstRun = false)
            filtered.isEmpty() -> VideoLibraryUiState.NoResult(keyword)
            else -> VideoLibraryUiState.Ready(
                videos = filtered,
                sortMode = data.sortMode,
                roots = data.roots,
                recent = data.recent,
            )
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            if (_refreshing.value) return@launch
            _refreshing.value = true
            try {
                scanVideoLibrary().fold(
                    onSuccess = { count ->
                        thumbFailureStreak = 0
                        _events.send(VideoEvent.Message("扫描完成，共 $count 个视频"))
                    },
                    onFailure = { t ->
                        _events.send(VideoEvent.Message(t.message ?: "扫描失败，请稍后重试"))
                    },
                )
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun onSortChange(mode: VideoSortMode) {
        viewModelScope.launch { preferencesRepository.setVideoSortMode(mode) }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onAddRoot(path: String) {
        viewModelScope.launch {
            addVideoRoot(path).fold(
                onSuccess = { root ->
                    _events.send(VideoEvent.Message("已添加「${root.displayName}」，开始扫描"))
                    onRefresh()
                },
                onFailure = { t ->
                    _events.send(VideoEvent.Message("添加失败：${t.message ?: "路径无效"}"))
                },
            )
        }
    }

    fun onRemoveRoot(root: VideoRoot) {
        viewModelScope.launch {
            removeVideoRoot(root.id).fold(
                onSuccess = { _events.send(VideoEvent.Message("已移除「${root.displayName}」")) },
                onFailure = { t -> _events.send(VideoEvent.Message("移除失败：${t.message ?: "未知错误"}")) },
            )
        }
    }

    fun onRandom() {
        viewModelScope.launch {
            randomVideo().fold(
                onSuccess = { key -> _events.send(VideoEvent.NavigateToPlayer(key.id)) },
                onFailure = { t -> _events.send(VideoEvent.Message(t.message ?: "没有可随机的视频")) },
            )
        }
    }

    fun onOpenVideo(key: VideoKey) {
        _events.trySend(VideoEvent.NavigateToPlayer(key.id))
    }

    fun onLongPressVideo(summary: VideoSummary) {
        _actionTarget.value = summary
    }

    fun onDismissAction() {
        _actionTarget.value = null
    }

    fun onRequestDelete(summary: VideoSummary) {
        _actionTarget.value = null
        _pendingDelete.value = summary
    }

    fun onDismissDelete() {
        _pendingDelete.value = null
    }

    fun onRequestRename(summary: VideoSummary) {
        _actionTarget.value = null
        _pendingRename.value = summary
    }

    fun onDismissRename() {
        _pendingRename.value = null
    }

    /** 重命名只改主名（校验与扩展名拼接在 UseCase）；列表由 DB Flow 自动刷新。 */
    fun onConfirmRename(baseName: String) {
        val target = _pendingRename.value ?: return
        _pendingRename.value = null
        viewModelScope.launch {
            renameVideo(target.key, baseName).fold(
                onSuccess = { newKey ->
                    _events.send(VideoEvent.Message("已重命名为「${newKey.fileName}」"))
                },
                onFailure = { t ->
                    _events.send(VideoEvent.Message(t.message ?: "重命名失败，文件可能被占用"))
                },
            )
        }
    }

    fun onConfirmDelete() {
        val target = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            deleteVideo(target.key).fold(
                onSuccess = { _events.send(VideoEvent.Message("已删除「${target.displayName}」")) },
                onFailure = { t ->
                    _events.send(VideoEvent.Message(t.message ?: "删除失败，文件可能被占用"))
                },
            )
        }
    }

    /**
     * 卡片进入可见区时触发缩略图（DESIGN v2 §3.6）：去重入队 + 全局 3 并发；
     * 连续失败 10 个后熔断，避免坏文件持续消耗 CPU（下次刷新重置）。
     */
    fun onThumbnailVisible(summary: VideoSummary) {
        if (summary.thumbState != VideoThumbnailState.PENDING) return
        if (thumbFailureStreak >= THUMB_FAILURE_LIMIT) return
        if (!thumbRequested.add(summary.key.id)) return
        viewModelScope.launch {
            thumbGate.withPermit {
                val ok = runCatching { ensureThumbnail(summary.key) }.getOrDefault(false)
                if (ok) {
                    thumbFailureStreak = 0
                } else {
                    thumbFailureStreak++
                }
            }
        }
    }

    companion object {
        private const val THUMB_FAILURE_LIMIT = 10
    }
}
