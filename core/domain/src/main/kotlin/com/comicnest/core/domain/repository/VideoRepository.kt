package com.comicnest.core.domain.repository

import com.comicnest.core.model.RecentVideo
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoProgress
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary
import kotlinx.coroutines.flow.Flow

/**
 * 视频域数据访问契约（DESIGN v2 §3.3，由 `:core:data` 实现）。
 * 与漫画域 `LibraryRepository` 完全独立：不共享 DAO、不共享库根。
 */
interface VideoRepository {

    /** 观察已导入视频根。 */
    fun observeRoots(): Flow<List<VideoRoot>>

    /** 校验存在/是目录/可读后新增（同路径去重）。 */
    suspend fun addRoot(path: String): Result<VideoRoot>

    /** 移除根：级联清理该根的缓存、观看记录与缩略图文件。 */
    suspend fun removeRoot(rootId: String): Result<Unit>

    /** 视频总览（NAME 自然序 / IMPORT_TIME 导入时间倒序）。 */
    fun observeVideos(sort: VideoSortMode): Flow<List<VideoSummary>>

    /** 全量重扫并整表替换（同 key 且 size+mtime 未变时继承缩略图与导入时间），返回视频总数。 */
    suspend fun refresh(): Result<Int>

    /** 单个视频详情（播放页入口校验）。 */
    suspend fun getVideo(key: VideoKey): Result<VideoSummary>

    /**
     * 生成并回写缩略图（供 `EnsureVideoThumbnailUseCase` 调用）。
     * 已 READY/FAILED、文件缺失时直接跳过；成功回写 `thumb_path/duration_ms`，失败标记 FAILED。
     * 之所以收口在仓储：domain 层不得依赖 Android 的 `:source:video`。
     */
    suspend fun generateThumbnail(key: VideoKey): Result<Unit>

    /** 缩略图生成成功后回写（幂等）。 */
    suspend fun saveThumbnail(key: VideoKey, path: String, durationMs: Long?): Result<Unit>

    /** 标记 FAILED（避免反复重试；refresh 时重置为 PENDING）。 */
    suspend fun markThumbnailFailed(key: VideoKey): Result<Unit>

    /** 物理删除文件 + 清 MediaStore + 清缩略图 + 删进度行 + 移出缓存（§3.7）。 */
    suspend fun deleteVideo(key: VideoKey): Result<Unit>

    /**
     * 重命名物理文件并迁移缓存/观看记录（[VideoKey] 随文件名变化），返回新 key。
     * 文件重命名失败时不得改动任何数据库行；DB 失败时需把文件名回滚。
     */
    suspend fun renameVideo(key: VideoKey, newFileName: String): Result<VideoKey>

    /** 保存进度并在同一事务内淘汰到最多 3 条。 */
    suspend fun saveProgress(key: VideoKey, positionMs: Long, durationMs: Long): Result<Unit>

    /** 单条进度（无则 null → 从头播）。 */
    suspend fun getProgress(key: VideoKey): VideoProgress?

    /** 最近观看（≤3），关联不到的（文件已删）直接丢弃。 */
    fun observeRecent(): Flow<List<RecentVideo>>
}
