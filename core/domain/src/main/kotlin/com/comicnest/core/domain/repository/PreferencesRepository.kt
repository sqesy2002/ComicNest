package com.comicnest.core.domain.repository

import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.SortMode
import com.comicnest.core.model.VideoOrientationMode
import com.comicnest.core.model.VideoSortMode
import kotlinx.coroutines.flow.Flow

/** 用户偏好契约（DataStore 实现）。视频域排序与漫画域排序互不影响（DESIGN v2 V12）。 */
interface PreferencesRepository {

    fun observeSortMode(): Flow<SortMode>

    suspend fun setSortMode(mode: SortMode)

    fun observeReaderMode(): Flow<ReaderMode>

    suspend fun setReaderMode(mode: ReaderMode)

    fun observeVideoSortMode(): Flow<VideoSortMode>

    suspend fun setVideoSortMode(mode: VideoSortMode)

    /** 默认视频朝向（DESIGN v2 §11.6 R6），缺省为竖屏。 */
    fun observeVideoOrientation(): Flow<VideoOrientationMode>

    suspend fun setVideoOrientation(mode: VideoOrientationMode)
}
