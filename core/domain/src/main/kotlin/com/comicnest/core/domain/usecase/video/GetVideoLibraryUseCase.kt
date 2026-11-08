package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.model.VideoLibraryUiData
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.VideoRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 合并「排序 → 视频列表」「库根」「继续观看」为单一 [VideoLibraryUiData] 流（DESIGN v2 §3.3）。
 * 排序变更时以 flatMapLatest 重新订阅视频流。
 */
class GetVideoLibraryUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<VideoLibraryUiData> =
        preferencesRepository.observeVideoSortMode().flatMapLatest { sort ->
            combine(
                videoRepository.observeVideos(sort),
                videoRepository.observeRoots(),
                videoRepository.observeRecent(),
            ) { videos, roots, recent ->
                VideoLibraryUiData(videos = videos, sortMode = sort, roots = roots, recent = recent)
            }
        }
}
