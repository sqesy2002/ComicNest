package com.comicnest.feature.library.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.model.LibraryUiData
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.AddLibraryRootUseCase
import com.comicnest.core.domain.usecase.GetLibraryUseCase
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.domain.usecase.RandomComicUseCase
import com.comicnest.core.domain.usecase.ScanLibraryUseCase
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.SortMode
import com.comicnest.core.ui.component.FavoritesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 书架 UI 状态（密封状态机，DESIGN §3.7/§8.3）。 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Ready(
        val comics: List<ComicSummary>,
        val sortMode: SortMode,
        val roots: List<LibraryRoot>,
    ) : LibraryUiState

    /** [firstRun] 为 true 表示尚未导入任何库根（引导添加）。 */
    data class Empty(val firstRun: Boolean) : LibraryUiState
    /** 有漫画但搜索无命中（[query] 为当前关键词）；不显示添加入口（DESIGN §10.2）。 */
    data class NoResult(val query: String) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

/** 一次性事件（导航/提示），避免重放（DESIGN §8.3）。 */
sealed interface LibraryEvent {
    data class NavigateToReader(val comicKeyId: String) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibrary: GetLibraryUseCase,
    private val scanLibrary: ScanLibraryUseCase,
    private val addRoot: AddLibraryRootUseCase,
    private val randomComic: RandomComicUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val collectionRepository: CollectionRepository,
    private val manageCollection: ManageCollectionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _favorites = MutableStateFlow<FavoritesUiState?>(null)
    val favorites: StateFlow<FavoritesUiState?> = _favorites.asStateFlow()

    /** 手动刷新进行中标记：不影响已有内容显示，仅在 UI 顶部叠加进度条（避免整屏 Loading 卡住）。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var favoritesJob: Job? = null

    /** 首页搜索关键词（纯 UI 临时态，不持久化；DESIGN §10.2）。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 全库漫画总数（不受搜索过滤影响，供抽屉分组固定划分用；DESIGN §10.3）。 */
    private val _comicCount = MutableStateFlow(0)
    val comicCount: StateFlow<Int> = _comicCount.asStateFlow()

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(getLibrary(), _query) { data, q -> data to q }
                .collect { (data, q) -> render(data, q) }
        }
    }

    private fun render(data: LibraryUiData, query: String) {
        // 错误态优先，不被后续流覆盖（手动刷新失败保留提示）
        if (_uiState.value is LibraryUiState.Error) return
        _comicCount.value = data.comics.size
        val keyword = query.trim()
        val filtered = if (keyword.isEmpty()) {
            data.comics
        } else {
            data.comics.filter { it.key.displayName.contains(keyword, ignoreCase = true) }
        }
        _uiState.value = when {
            data.roots.isEmpty() -> LibraryUiState.Empty(firstRun = true)
            data.comics.isEmpty() -> LibraryUiState.Empty(firstRun = false)
            filtered.isEmpty() -> LibraryUiState.NoResult(query = keyword)
            else -> LibraryUiState.Ready(filtered, data.sortMode, data.roots)
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            if (_refreshing.value) return@launch
            _refreshing.value = true
            try {
                scanLibrary().fold(
                    onSuccess = { count ->
                        _events.send(LibraryEvent.Message("扫描完成，共 $count 部漫画"))
                        // comicDao 变更后 getLibrary 流自动驱动 Ready/Empty
                    },
                    onFailure = { t ->
                        _events.send(LibraryEvent.Message(t.message ?: "扫描失败，请稍后重试"))
                    },
                )
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun onSortChange(mode: SortMode) {
        viewModelScope.launch { preferencesRepository.setSortMode(mode) }
    }

    /** 搜索关键词变化（即时内存过滤）。 */
    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onAddRoot(path: String) {
        viewModelScope.launch {
            addRoot(path).fold(
                onSuccess = {
                    _events.send(LibraryEvent.Message("已添加漫画库，开始扫描"))
                    onRefresh()
                },
                onFailure = { t ->
                    _events.send(LibraryEvent.Message("添加失败：${t.message ?: "路径无效"}"))
                },
            )
        }
    }

    fun onRandom() {
        viewModelScope.launch {
            randomComic().fold(
                onSuccess = { key -> _events.send(LibraryEvent.NavigateToReader(key.id)) },
                onFailure = { t -> _events.send(LibraryEvent.Message(t.message ?: "没有可随机的漫画")) },
            )
        }
    }

    fun onOpenComic(key: ComicKey) {
        _events.trySend(LibraryEvent.NavigateToReader(key.id))
    }

    /** 长按漫画：加载全部收藏夹与该漫画已属收藏夹，弹出多选对话框（D7）。 */
    fun onLongPressComic(key: ComicKey) {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            combine(
                collectionRepository.observeCollections(),
                collectionRepository.collectionsOf(key),
            ) { all, mine -> FavoritesUiState(key, all, mine.map { it.id }.toSet()) }
                .collect { _favorites.value = it }
        }
    }

    /** 勾选/取消某收藏夹：加入或移出，成功后 combine 自动刷新选中态。 */
    fun onToggleFavorite(collectionId: Long, add: Boolean) {
        val key = _favorites.value?.key ?: return
        viewModelScope.launch {
            val result = if (add) manageCollection.addComic(collectionId, key)
            else manageCollection.removeComic(collectionId, key)
            result.onFailure { t -> _events.trySend(LibraryEvent.Message(t.message ?: "操作失败")) }
        }
    }

    fun onDismissFavorites() {
        favoritesJob?.cancel()
        _favorites.value = null
    }
}
