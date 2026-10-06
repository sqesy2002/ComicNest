package com.comicnest.core.data.repository

import com.comicnest.core.common.result.runCatchingIo
import com.comicnest.core.data.db.dao.ProgressDao
import com.comicnest.core.data.db.entity.ReadProgressEntity
import com.comicnest.core.data.mapper.toEntity
import com.comicnest.core.data.mapper.toModel
import com.comicnest.core.domain.repository.ProgressRepository
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ReadProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 进度仓储实现：保存与 LRU 淘汰同事务，保证最多 [LRU_KEEP] 条（D6）。 */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val progressDao: ProgressDao,
) : ProgressRepository {

    override suspend fun getProgress(key: ComicKey): ReadProgress? =
        progressDao.getProgress(key.id)?.toModel()

    override suspend fun saveProgress(key: ComicKey, pageIndex: Int, total: Int): Result<Unit> =
        runCatchingIo {
            val entity = ReadProgress(
                comicKeyId = key.id,
                pageIndex = pageIndex,
                totalPages = total,
                updatedAt = System.currentTimeMillis(),
            ).toEntity()
            progressDao.saveAndEvict(entity, LRU_KEEP)
        }

    override fun observeRecent(): Flow<List<ReadProgress>> =
        progressDao.observeRecent(LRU_KEEP).map { list -> list.map(ReadProgressEntity::toModel) }

    companion object {
        const val LRU_KEEP = 5
    }
}
