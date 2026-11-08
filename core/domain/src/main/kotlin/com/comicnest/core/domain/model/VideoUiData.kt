package com.comicnest.core.domain.model

import com.comicnest.core.model.RecentVideo
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary

/** [GetVideoLibraryUseCase] 输出：视频列表 + 排序 + 库根 + 继续观看（四流合并）。 */
data class VideoLibraryUiData(
    val videos: List<VideoSummary>,
    val sortMode: VideoSortMode,
    val roots: List<VideoRoot>,
    val recent: List<RecentVideo>,
)
