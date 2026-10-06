package com.comicnest.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.comicnest.core.data.db.entity.CollectionEntity
import com.comicnest.core.data.db.entity.CollectionEntryEntity
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.db.entity.LibraryRootEntity
import com.comicnest.core.data.db.entity.ReadProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RootDao {

    @Query("SELECT * FROM library_root ORDER BY added_at ASC")
    fun observeAll(): Flow<List<LibraryRootEntity>>

    @Query("SELECT * FROM library_root WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): LibraryRootEntity?

    @Query("SELECT * FROM library_root WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LibraryRootEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LibraryRootEntity): Long

    @Query("DELETE FROM library_root WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM comic_cache WHERE root_id = :rootId")
    suspend fun deleteComicsOfRoot(rootId: String)
}

@Dao
interface ComicDao {

    @Query("SELECT * FROM comic_cache ORDER BY comic_name COLLATE NOCASE ASC")
    fun observeByName(): Flow<List<ComicCacheEntity>>

    @Query("SELECT * FROM comic_cache ORDER BY imported_at DESC")
    fun observeByImportTime(): Flow<List<ComicCacheEntity>>

    @Query("SELECT * FROM comic_cache WHERE comic_key_id = :keyId LIMIT 1")
    suspend fun getById(keyId: String): ComicCacheEntity?

    @Query("SELECT comic_key_id FROM comic_cache")
    suspend fun getAllKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ComicCacheEntity>)

    @Query("DELETE FROM comic_cache")
    suspend fun deleteAll()

    /** 整表重建，原子事务保证扫描一致性（DESIGN §3.5）。 */
    @Transaction
    suspend fun replaceAll(entities: List<ComicCacheEntity>) {
        deleteAll()
        insertAll(entities)
    }
}

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collection ORDER BY created_at ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(entity: CollectionEntity): Long

    @Update
    suspend fun updateCollection(entity: CollectionEntity)

    @Query("UPDATE collection SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    @Query("DELETE FROM collection WHERE id = :id")
    suspend fun deleteCollectionById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entity: CollectionEntryEntity): Long

    @Query("DELETE FROM collection_entry WHERE collection_id = :collectionId AND comic_key_id = :keyId")
    suspend fun deleteEntry(collectionId: Long, keyId: String)

    /** 收藏夹内漫画（联表，仅返回当前缓存中仍存在的漫画）。 */
    @Query(
        "SELECT c.* FROM comic_cache c INNER JOIN collection_entry e " +
            "ON c.comic_key_id = e.comic_key_id WHERE e.collection_id = :collectionId " +
            "ORDER BY c.comic_name COLLATE NOCASE ASC",
    )
    fun observeComicsOfCollection(collectionId: Long): Flow<List<ComicCacheEntity>>

    /** 某漫画所属收藏夹（多对多）。 */
    @Query(
        "SELECT col.* FROM collection col INNER JOIN collection_entry e " +
            "ON col.id = e.collection_id WHERE e.comic_key_id = :keyId",
    )
    fun observeCollectionsOfComic(keyId: String): Flow<List<CollectionEntity>>

    /** 全部 entry 引用的漫画键（用于失效校验）。 */
    @Query("SELECT DISTINCT comic_key_id FROM collection_entry")
    suspend fun getAllEntryKeys(): List<String>

    /** 删除指向失效漫画的 entry，返回删除数。 */
    @Query("DELETE FROM collection_entry WHERE comic_key_id IN (:keys)")
    suspend fun deleteEntriesByKeys(keys: List<String>): Int
}

@Dao
interface ProgressDao {

    @Query("SELECT * FROM read_progress WHERE comic_key_id = :keyId LIMIT 1")
    suspend fun getProgress(keyId: String): ReadProgressEntity?

    @Query("SELECT * FROM read_progress ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReadProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadProgressEntity)

    /** 保留最近 [keep] 条，淘汰其余（LRU）。 */
    @Query(
        "DELETE FROM read_progress WHERE comic_key_id NOT IN " +
            "(SELECT comic_key_id FROM read_progress ORDER BY updated_at DESC LIMIT :keep)",
    )
    suspend fun evictOldest(keep: Int)

    @Query("SELECT COUNT(*) FROM read_progress")
    suspend fun count(): Int

    /** upsert 与淘汰同事务，保证行数 ≤keep（D6）。 */
    @Transaction
    suspend fun saveAndEvict(entity: ReadProgressEntity, keep: Int) {
        upsert(entity)
        evictOldest(keep)
    }
}
