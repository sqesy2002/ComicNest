package com.comicnest.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.usecase.AddLibraryRootUseCase
import com.comicnest.core.domain.usecase.ScanLibraryUseCase
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReaderMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页状态（DESIGN §3.7 / §9.8）：库根列表 + 默认阅读模式。 */
data class SettingsUiState(
    val roots: List<LibraryRoot> = emptyList(),
    val readerMode: ReaderMode = ReaderMode.WEBTOON,
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
}

/**
 * 设置：库根增/删（删除连带失效其漫画缓存 D2），添加成功后触发重扫；默认阅读模式读写偏好。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val addRoot: AddLibraryRootUseCase,
    private val scanLibrary: ScanLibraryUseCase,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        libraryRepository.observeRoots(),
        preferencesRepository.observeReaderMode(),
    ) { roots, mode ->
        SettingsUiState(roots, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAddRoot(path: String) {
        viewModelScope.launch {
            addRoot(path).fold(
                onSuccess = { root ->
                    _events.send(SettingsEvent.Message("已添加「${root.displayName}」"))
                    scanLibrary()
                },
                onFailure = { t ->
                    _events.send(SettingsEvent.Message("添加失败：${t.message ?: "路径无效"}"))
                },
            )
        }
    }

    fun onRemoveRoot(root: LibraryRoot) {
        viewModelScope.launch {
            libraryRepository.removeRoot(root.id).fold(
                onSuccess = { _events.send(SettingsEvent.Message("已移除「${root.displayName}」")) },
                onFailure = { t -> _events.send(SettingsEvent.Message("移除失败：${t.message ?: "未知错误"}")) },
            )
        }
    }

    fun onReaderModeChange(mode: ReaderMode) {
        viewModelScope.launch { preferencesRepository.setReaderMode(mode) }
    }
}
