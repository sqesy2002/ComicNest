package com.comicnest.core.data.repository

import com.comicnest.core.common.result.runCatchingIo
import com.comicnest.core.data.db.dao.CollectionDao
import com.comicnest.core.data.db.dao.ComicDao
import com.comicnest.core.data.db.entity.CollectionEntity
import com.comicnest.core.data.db.entity.CollectionEntryEntity
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.mapper.toModel
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 收藏仓储实现：多对多关联与失效清理（D7）。 */
@Singleton
class CollectionRepositoryImpl @Inject constructor(
    private val collectionDao: CollectionDao,
    private val comicDao: ComicDao,
) : CollectionRepository {

    override fun observeCollections(): Flow<List<Collection>> =
        collectionDao.observeCollections().map { list -> list.map(CollectionEntity::toModel) }

    override suspend fun createCollection(name: String): Result<Collection> = runCatchingIo {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "收藏夹名称不能为空" }
        require(collectionDao.getByName(trimmed) == null) { "已存在同名收藏夹：$trimmed" }
        val now = System.currentTimeMillis()
        val id = collectionDao.insertCollection(CollectionEntity(id = 0L, name = trimmed, createdAt = now))
        Collection(id = id, name = trimmed, createdAt = now)
    }

    override suspend fun renameCollection(id: Long, name: String): Result<Unit> = runCatchingIo {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "收藏夹名称不能为空" }
        val dup = collectionDao.getByName(trimmed)
        require(dup == null || dup.id == id) { "已存在同名收藏夹：$trimmed" }
        collectionDao.renameCollection(id, trimmed)
    }

    override suspend fun deleteCollection(id: Long): Result<Unit> = runCatchingIo {
        // 外键级联删除 collection_entry
        collectionDao.deleteCollectionById(id)
    }

    override fun observeEntries(collectionId: Long): Flow<List<ComicSummary>> =
        collectionDao.observeComicsOfCollection(collectionId).map { list -> list.map(ComicCacheEntity::toModel) }

    override suspend fun addComic(collectionId: Long, key: ComicKey): Result<Unit> = runCatchingIo {
        collectionDao.insertEntry(CollectionEntryEntity(collectionId = collectionId, comicKeyId = key.id))
        Unit // 幂等：主键冲突 IGNORE
    }

    override suspend fun removeComic(collectionId: Long, key: ComicKey): Result<Unit> = runCatchingIo {
        collectionDao.deleteEntry(collectionId, key.id)
    }

    override fun collectionsOf(key: ComicKey): Flow<List<Collection>> =
        collectionDao.observeCollectionsOfComic(key.id).map { list -> list.map(CollectionEntity::toModel) }

    override suspend fun cleanupInvalid(): Result<Int> = runCatchingIo {
        val valid = comicDao.getAllKeys().toSet()
        val invalid = collectionDao.getAllEntryKeys().filterNot { it in valid }
        if (invalid.isEmpty()) 0 else collectionDao.deleteEntriesByKeys(invalid)
    }
}
