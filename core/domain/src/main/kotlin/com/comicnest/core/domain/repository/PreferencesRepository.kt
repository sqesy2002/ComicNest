package com.comicnest.core.domain.repository

import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.SortMode
import kotlinx.coroutines.flow.Flow

/** 用户偏好契约（DataStore 实现）。 */
interface PreferencesRepository {

    fun observeSortMode(): Flow<SortMode>

    suspend fun setSortMode(mode: SortMode)

    fun observeReaderMode(): Flow<ReaderMode>

    suspend fun setReaderMode(mode: ReaderMode)
}
