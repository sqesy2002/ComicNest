package com.comicnest.feature.collection.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 收藏夹详情状态（DESIGN §3.7）。 */
sealed interface CollectionDetailUiState {
    data object Loading : CollectionDetailUiState
    data class Ready(val name: String, val comics: List<ComicSummary>) : CollectionDetailUiState
    data object Empty : CollectionDetailUiState
    data object NotFound : CollectionDetailUiState
}

sealed interface CollectionDetailEvent {
    data class Message(val text: String) : CollectionDetailEvent
    data object NavigateBack : CollectionDetailEvent
}

/**
 * 收藏夹详情：展示该夹内漫画；支持移除（长按卡片）、重命名/删除（顶栏菜单）。
 * 名称来自 [CollectionRepository.observeCollections] 过滤当前 id。
 */
@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
    private val manage: ManageCollectionUseCase,
) : ViewModel() {

    private val collectionId: Long = savedStateHandle.get<Long>(ARG_ID) ?: -1L

    private val _uiState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<CollectionDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                collectionRepository.observeCollections(),
                collectionRepository.observeEntries(collectionId),
            ) { collections, comics ->
                val name = collections.find { it.id == collectionId }?.name
                name to comics
            }.collect { (name, comics) ->
                _uiState.value = when {
                    name == null -> CollectionDetailUiState.NotFound
                    comics.isEmpty() -> CollectionDetailUiState.Empty
                    else -> CollectionDetailUiState.Ready(name, comics)
                }
            }
        }
    }

    fun onRemoveComic(key: ComicKey) {
        viewModelScope.launch {
            manage.removeComic(collectionId, key).fold(
                onSuccess = { _events.send(CollectionDetailEvent.Message("已移出收藏")) },
                onFailure = { _events.send(CollectionDetailEvent.Message(it.message ?: "移除失败")) },
            )
        }
    }

    fun onRename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            manage.rename(collectionId, trimmed).fold(
                onSuccess = { _events.send(CollectionDetailEvent.Message("已重命名")) },
                onFailure = { _events.send(CollectionDetailEvent.Message(it.message ?: "重命名失败")) },
            )
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            manage.delete(collectionId).fold(
                onSuccess = { _events.send(CollectionDetailEvent.NavigateBack) },
                onFailure = { _events.send(CollectionDetailEvent.Message(it.message ?: "删除失败")) },
            )
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
