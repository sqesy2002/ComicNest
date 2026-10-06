package com.comicnest.core.domain.repository

import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import kotlinx.coroutines.flow.Flow

/** 收藏夹契约（多对多，D7）。 */
interface CollectionRepository {

    fun observeCollections(): Flow<List<Collection>>

    /** 新建（重名校验）。 */
    suspend fun createCollection(name: String): Result<Collection>

    suspend fun renameCollection(id: Long, name: String): Result<Unit>

    /** 删除（连带关联）。 */
    suspend fun deleteCollection(id: Long): Result<Unit>

    /** 收藏夹内漫画。 */
    fun observeEntries(collectionId: Long): Flow<List<ComicSummary>>

    /** 加入（幂等）。 */
    suspend fun addComic(collectionId: Long, key: ComicKey): Result<Unit>

    suspend fun removeComic(collectionId: Long, key: ComicKey): Result<Unit>

    /** 某漫画所属收藏夹（多对多）。 */
    fun collectionsOf(key: ComicKey): Flow<List<Collection>>

    /** 校验指向有效性，删除失效 entry，返回清理数（D7）。 */
    suspend fun cleanupInvalid(): Result<Int>
}
