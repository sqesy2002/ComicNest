package com.comicnest.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.comicnest.core.data.db.entity.VideoCacheEntity
import com.comicnest.core.data.db.entity.VideoProgressEntity
import com.comicnest.core.data.db.entity.VideoRootEntity
import kotlinx.coroutines.flow.Flow

/** 视频根 CRUD（与 v1 `RootDao` 同构，独立表）。 */
@Dao
interface VideoRootDao {

    @Query("SELECT * FROM video_root ORDER BY added_at ASC")
    fun observeAll(): Flow<List<VideoRootEntity>>

    @Query("SELECT * FROM video_root ORDER BY added_at ASC")
    suspend fun getAllOnce(): List<VideoRootEntity>

    @Query("SELECT * FROM video_root WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): VideoRootEntity?

    @Query("SELECT * FROM video_root WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VideoRootEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: VideoRootEntity): Long

    @Query("DELETE FROM video_root WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * 视频缓存 CRUD。`observeAll()` 不排序（自然序由 data 层按 [com.comicnest.core.common.util.naturalOrderCompare] 处理，
 * SQLite 无法表达自然序）。
 */
@Dao
interface VideoDao {

    @Query("SELECT * FROM video_cache")
    fun observeAll(): Flow<List<VideoCacheEntity>>

    @Query("SELECT * FROM video_cache ORDER BY imported_at DESC")
    fun observeAllByImportedDesc(): Flow<List<VideoCacheEntity>>

    @Query("SELECT * FROM video_cache WHERE video_key_id = :keyId LIMIT 1")
    suspend fun getById(keyId: String): VideoCacheEntity?

    @Query("SELECT * FROM video_cache WHERE root_id = :rootId")
    suspend fun getByRoot(rootId: String): List<VideoCacheEntity>

    @Query("SELECT thumb_path FROM video_cache WHERE thumb_path IS NOT NULL")
    suspend fun getAllThumbPaths(): List<String>

    @Query("SELECT COUNT(*) FROM video_cache")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<VideoCacheEntity>)

    @Query("DELETE FROM video_cache WHERE root_id = :rootId")
    suspend fun deleteByRoot(rootId: String)

    @Query("DELETE FROM video_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM video_cache WHERE video_key_id = :keyId")
    suspend fun deleteById(keyId: String)

    /** 回写缩略图与时长（生成成功后调用）。 */
    @Query(
        "UPDATE video_cache SET thumb_path = :thumbPath, thumb_state = :thumbState, " +
            "duration_ms = :durationMs, last_modified = :lastModified WHERE video_key_id = :keyId",
    )
    suspend fun updateThumb(
        keyId: String,
        thumbPath: String?,
        thumbState: Int,
        durationMs: Long?,
        lastModified: Long,
    )

    @Query("UPDATE video_cache SET thumb_state = :thumbState WHERE video_key_id = :keyId")
    suspend fun updateThumbState(keyId: String, thumbState: Int)

    /** 单根扫描结果整体替换（原子，避免半新半旧）。 */
    @Transaction
    suspend fun replaceRoot(rootId: String, entities: List<VideoCacheEntity>) {
        deleteByRoot(rootId)
        insertAll(entities)
    }

    /**
     * 重命名后替换缓存行主键（旧行删除 + 新行插入），并把观看记录迁移到新 key，同事务。
     * 失败则整体回滚，由调用方负责把文件名改回去。
     */
    @Transaction
    suspend fun renameCache(oldKeyId: String, entity: VideoCacheEntity) {
        deleteById(oldKeyId)
        insertAll(listOf(entity))
        moveProgressKey(oldKeyId, entity.videoKeyId)
    }

    /** 重命名时迁移观看记录主键（video_key_id 由文件名派生）。 */
    @Query("UPDATE video_progress SET video_key_id = :newKeyId WHERE video_key_id = :oldKeyId")
    suspend fun moveProgressKey(oldKeyId: String, newKeyId: String)
}

/** 观看记录 DAO：LRU=3，upsert 与淘汰同事务。 */
@Dao
interface VideoProgressDao {

    @Query("SELECT * FROM video_progress WHERE video_key_id = :keyId LIMIT 1")
    suspend fun getById(keyId: String): VideoProgressEntity?

    @Query("SELECT * FROM video_progress ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<VideoProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VideoProgressEntity)

    /** 保留最近 [keep] 条，淘汰其余。 */
    @Query(
        "DELETE FROM video_progress WHERE video_key_id NOT IN " +
            "(SELECT video_key_id FROM video_progress ORDER BY updated_at DESC LIMIT :keep)",
    )
    suspend fun evictOldest(keep: Int)

    @Query("DELETE FROM video_progress WHERE video_key_id = :keyId")
    suspend fun deleteById(keyId: String)

    /** 移除某视频根时，连带清理其下所有观看记录。 */
    @Query(
        "DELETE FROM video_progress WHERE video_key_id IN " +
            "(SELECT video_key_id FROM video_cache WHERE root_id = :rootId)",
    )
    suspend fun deleteByRoot(rootId: String)

    @Query("SELECT COUNT(*) FROM video_progress")
    suspend fun count(): Int

    @Transaction
    suspend fun saveAndEvict(entity: VideoProgressEntity, keep: Int) {
        upsert(entity)
        evictOldest(keep)
    }
}
