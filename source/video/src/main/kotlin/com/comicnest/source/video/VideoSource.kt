package com.comicnest.source.video

import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSummary

/** 视频媒体元信息（DESIGN v2 §3.4）。 */
data class VideoMeta(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)

/** 缩略图生成结果：[path] 为落盘后的缓存绝对路径。 */
class ThumbnailResult(
    val path: String,
    val durationMs: Long,
)

/**
 * 视频来源抽象（与 v1 `ComicSource` 同构，DESIGN v2 §3.4）。
 * 本期唯一实现 [LocalVideoSource]；不持久化、不做 UI。
 */
interface VideoSource {

    val id: String

    val label: String

    /** 扫描一个视频根（只取直接下一级的视频文件）。 */
    suspend fun scanLibrary(root: VideoRoot): Result<List<VideoSummary>>

    /** 读取单个文件的时长/分辨率/旋转角。 */
    suspend fun readMeta(absPath: String): Result<VideoMeta>

    /**
     * 生成“中间帧”缩略图并落盘，返回缓存路径与时长。
     * 并发与缓存容量由实现内部收敛（DESIGN v2 §3.6）。
     */
    suspend fun ensureThumbnail(
        key: VideoKey,
        absPath: String,
        sizeBytes: Long,
        lastModified: Long,
    ): Result<ThumbnailResult>

    /** 删除单个缩略图缓存（幂等）。 */
    fun deleteThumbnail(path: String?)

    /**
     * 在同一目录内重命名物理文件，并同步 MediaStore 索引。
     * 失败返回 failure 且不抛出；调用方必须保证此时不动数据库。
     */
    suspend fun renameFile(oldPath: String, newPath: String): Result<Unit>

    /**
     * 把旧缩略图迁移到新 key 对应的缓存路径（重命名不重新解码）。
     * 旧路径为空/文件不存在/迁移失败返回 null，由调用方置 PENDING 重新生成。
     */
    fun moveThumbnail(
        oldPath: String?,
        newKey: VideoKey,
        sizeBytes: Long,
        lastModified: Long,
    ): String?

    /**
     * 清理孤儿缩略图：缓存目录中不在 [validPaths] 内、且创建时间早于 1 天的文件。
     */
    fun cleanupThumbnails(validPaths: Collection<String>)

    /** 尽力清理系统 MediaStore 中该文件的索引（删除失败不抛出，DESIGN v2 §3.7）。 */
    suspend fun purgeMediaStore(absPath: String)

    /**
     * 等待来源侧对该文件的读取结束（缩略图/元数据），删除前调用以避免自伤占用（DESIGN v2 §11.2 R2）。
     * 默认无操作，便于测试替身实现。
     */
    suspend fun awaitFileIdle(absPath: String, timeoutMs: Long) = Unit
}
