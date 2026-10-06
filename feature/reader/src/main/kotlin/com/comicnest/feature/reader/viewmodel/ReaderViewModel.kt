package com.comicnest.feature.reader.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.usecase.OpenComicUseCase
import com.comicnest.core.domain.usecase.SaveProgressUseCase
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.ui.component.FavoritesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 阅读器 UI 状态（密封状态机，DESIGN §3.7）。 */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val pages: List<ComicPageRef>,
        val startIndex: Int,
        val title: String,
    ) : ReaderUiState

    data class Error(val message: String) : ReaderUiState
}

/**
 * 阅读器状态与进度编排（DESIGN §3.7）：
 * - 打开漫画定位到 [ReaderUiState.Ready.startIndex]（LRU 命中续读，否则首页 D6）。
 * - 翻页 [onPageChanged] 经节流（[THROTTLE_MS]）后调用 [SaveProgressUseCase]（LRU=5）。
 * - 阅读模式来自偏好并可在 WEBTOON↔PAGER 间切换，写回偏好（切换保持当前页由 UI 层负责）。
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val openComic: OpenComicUseCase,
    private val saveProgress: SaveProgressUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val collectionRepository: CollectionRepository,
    private val manageCollection: ManageCollectionUseCase,
) : ViewModel() {

    private val key: ComicKey = ComicKey.fromId(savedStateHandle.get<String>(ARG_COMIC_KEY_ID).orEmpty())

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _readerMode = MutableStateFlow(ReaderMode.WEBTOON)
    val readerMode: StateFlow<ReaderMode> = _readerMode.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /** 收藏多选对话框态（与书架同源，D7 多对多；DESIGN §10.4）。 */
    private val _favorites = MutableStateFlow<FavoritesUiState?>(null)
    val favorites: StateFlow<FavoritesUiState?> = _favorites.asStateFlow()

    /** 该漫画是否已属于任一收藏夹（驱动 ♡ 图标形态）。 */
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private var favoritesJob: Job? = null

    private var pendingSave: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.observeReaderMode().collect { _readerMode.value = it }
        }
        viewModelScope.launch {
            collectionRepository.collectionsOf(key).collect { _isFavorite.value = it.isNotEmpty() }
        }
        viewModelScope.launch {
            openComic(key).fold(
                onSuccess = { data ->
                    _currentPage.value = data.startIndex
                    _uiState.value = ReaderUiState.Ready(
                        pages = data.pages,
                        startIndex = data.startIndex,
                        title = key.displayName,
                    )
                },
                onFailure = { t ->
                    _uiState.value = ReaderUiState.Error(t.message ?: "无法打开漫画")
                },
            )
        }
    }

    /** 翻页回调：更新当前页并节流保存进度（取消前一次未落地的保存）。 */
    fun onPageChanged(index: Int) {
        val pages = (_uiState.value as? ReaderUiState.Ready)?.pages ?: return
        if (index !in pages.indices) return
        _currentPage.value = index
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(THROTTLE_MS)
            saveProgress(key, index, pages.size)
        }
    }

    /** 切换阅读模式并持久化（WEBTOON↔PAGER）。 */
    fun onToggleMode() {
        val next = if (_readerMode.value == ReaderMode.WEBTOON) ReaderMode.PAGER else ReaderMode.WEBTOON
        viewModelScope.launch { preferencesRepository.setReaderMode(next) }
    }

    /** ♡ 点击：加载全部收藏夹与该漫画已属收藏夹，弹出多选对话框（D7，与书架同源）。 */
    fun onFavoriteClick() {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            combine(
                collectionRepository.observeCollections(),
                collectionRepository.collectionsOf(key),
            ) { all, mine -> FavoritesUiState(key, all, mine.map { it.id }.toSet()) }
                .collect { _favorites.value = it }
        }
    }

    /** 勾选/取消某收藏夹：加入或移出，combine 自动刷新选中态与 ♡ 图标。 */
    fun onToggleFavorite(collectionId: Long, add: Boolean) {
        viewModelScope.launch {
            if (add) manageCollection.addComic(collectionId, key)
            else manageCollection.removeComic(collectionId, key)
        }
    }

    fun onDismissFavorites() {
        favoritesJob?.cancel()
        _favorites.value = null
    }

    companion object {
        const val ARG_COMIC_KEY_ID = "comicKeyId"
        const val THROTTLE_MS = 800L
    }
}
