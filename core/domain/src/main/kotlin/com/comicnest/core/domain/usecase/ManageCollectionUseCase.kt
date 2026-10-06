package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import javax.inject.Inject

/**
 * 收藏管理统一入口（DESIGN §3.3），聚合建/重命名/删/加/移，便于 UI 单一注入调用。
 */
class ManageCollectionUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
) {
    suspend fun create(name: String): Result<Collection> =
        collectionRepository.createCollection(name)

    suspend fun rename(id: Long, name: String): Result<Unit> =
        collectionRepository.renameCollection(id, name)

    suspend fun delete(id: Long): Result<Unit> =
        collectionRepository.deleteCollection(id)

    suspend fun addComic(collectionId: Long, key: ComicKey): Result<Unit> =
        collectionRepository.addComic(collectionId, key)

    suspend fun removeComic(collectionId: Long, key: ComicKey): Result<Unit> =
        collectionRepository.removeComic(collectionId, key)
}
