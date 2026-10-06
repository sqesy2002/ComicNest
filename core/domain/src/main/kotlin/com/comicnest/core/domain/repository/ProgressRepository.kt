package com.comicnest.core.domain.repository

import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ReadProgress
import kotlinx.coroutines.flow.Flow

/** 阅读进度契约（LRU=5，D6）。 */
interface ProgressRepository {

    /** 取进度（无则 null → 从第一页）。 */
    suspend fun getProgress(key: ComicKey): ReadProgress?

    /** 保存并执行 LRU=5 淘汰（D6）。 */
    suspend fun saveProgress(key: ComicKey, pageIndex: Int, total: Int): Result<Unit>

    /** 最近阅读（≤5）。 */
    fun observeRecent(): Flow<List<ReadProgress>>
}
