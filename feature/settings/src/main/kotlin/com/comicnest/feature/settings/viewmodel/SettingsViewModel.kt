package com.comicnest.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.domain.usecase.AddLibraryRootUseCase
import com.comicnest.core.domain.usecase.ScanLibraryUseCase
import com.comicnest.core.domain.usecase.video.AddVideoRootUseCase
import com.comicnest.core.domain.usecase.video.RemoveVideoRootUseCase
import com.comicnest.core.domain.usecase.video.ScanVideoLibraryUseCase
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.VideoRoot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页状态（DESIGN §3.7 / §9.8）：漫画库根 + 视频库根 + 默认阅读模式。 */
data class SettingsUiState(
    val roots: List<LibraryRoot> = emptyList(),
    val readerMode: ReaderMode = ReaderMode.WEBTOON,
    val videoRoots: List<VideoRoot> = emptyList(),
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
}

/**
 * 设置：漫画/视频库根增删（删除连带失效缓存）、添加成功后触发重扫；默认阅读模式读写偏好。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val addRoot: AddLibraryRootUseCase,
    private val scanLibrary: ScanLibraryUseCase,
    private val addVideoRoot: AddVideoRootUseCase,
    private val removeVideoRoot: RemoveVideoRootUseCase,
    private val scanVideoLibrary: ScanVideoLibraryUseCase,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        libraryRepository.observeRoots(),
        preferencesRepository.observeReaderMode(),
        videoRepository.observeRoots(),
    ) { roots, mode, videoRoots ->
        SettingsUiState(roots, mode, videoRoots)
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

    /** 添加视频库目录：校验通过后立即扫描一次（只取一级文件）。 */
    fun onAddVideoRoot(path: String) {
        viewModelScope.launch {
            addVideoRoot(path).fold(
                onSuccess = { root ->
                    _events.send(SettingsEvent.Message("已添加「${root.displayName}」"))
                    scanVideoLibrary()
                },
                onFailure = { t ->
                    _events.send(SettingsEvent.Message("添加失败：${t.message ?: "路径无效"}"))
                },
            )
        }
    }

    /** 移除视频库目录：连带清理该根缓存、观看记录与缩略图。 */
    fun onRemoveVideoRoot(root: VideoRoot) {
        viewModelScope.launch {
            removeVideoRoot(root.id).fold(
                onSuccess = { _events.send(SettingsEvent.Message("已移除「${root.displayName}」")) },
                onFailure = { t -> _events.send(SettingsEvent.Message("移除失败：${t.message ?: "未知错误"}")) },
            )
        }
    }
}
