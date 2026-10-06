package com.comicnest.feature.collection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.usecase.CleanupInvalidCollectionsUseCase
import com.comicnest.core.domain.usecase.ManageCollectionUseCase
import com.comicnest.core.model.Collection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 收藏夹列表状态（DESIGN §3.7）。 */
sealed interface CollectionListUiState {
    data object Loading : CollectionListUiState
    data class Ready(val collections: List<Collection>) : CollectionListUiState
    data object Empty : CollectionListUiState
}

sealed interface CollectionListEvent {
    data class Message(val text: String) : CollectionListEvent
}

/**
 * 收藏夹列表：观察收藏集合；进入即触发失效清理（D7），清理数 >0 提示；聚合新建/重命名/删除。
 */
@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val manage: ManageCollectionUseCase,
    private val cleanupInvalid: CleanupInvalidCollectionsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CollectionListUiState>(CollectionListUiState.Loading)
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    private val _events = Channel<CollectionListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            collectionRepository.observeCollections().collect { list ->
                _uiState.value = if (list.isEmpty()) CollectionListUiState.Empty
                else CollectionListUiState.Ready(list)
            }
        }
        viewModelScope.launch {
            cleanupInvalid().onSuccess { count ->
                if (count > 0) _events.send(CollectionListEvent.Message("已清理 $count 个失效收藏"))
            }
        }
    }

    fun onCreate(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch { _events.send(CollectionListEvent.Message("名称不能为空")) }
            return
        }
        viewModelScope.launch {
            manage.create(trimmed).fold(
                onSuccess = { _events.send(CollectionListEvent.Message("已创建「$trimmed」")) },
                onFailure = { _events.send(CollectionListEvent.Message(it.message ?: "创建失败")) },
            )
        }
    }

    fun onRename(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            manage.rename(id, trimmed).fold(
                onSuccess = { _events.send(CollectionListEvent.Message("已重命名")) },
                onFailure = { _events.send(CollectionListEvent.Message(it.message ?: "重命名失败")) },
            )
        }
    }

    fun onDelete(id: Long, name: String) {
        viewModelScope.launch {
            manage.delete(id).fold(
                onSuccess = { _events.send(CollectionListEvent.Message("已删除「$name」")) },
                onFailure = { _events.send(CollectionListEvent.Message(it.message ?: "删除失败")) },
            )
        }
    }
}
