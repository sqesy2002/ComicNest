package com.comicnest.core.data.repository

import com.comicnest.core.common.log.AppLog
import com.comicnest.core.common.result.runCatchingIo
import com.comicnest.core.common.util.naturalOrderCompare
import com.comicnest.core.data.db.dao.VideoDao
import com.comicnest.core.data.db.dao.VideoProgressDao
import com.comicnest.core.data.db.dao.VideoRootDao
import com.comicnest.core.data.db.entity.VideoCacheEntity
import com.comicnest.core.data.db.entity.VideoProgressEntity
import com.comicnest.core.data.db.entity.VideoRootEntity
import com.comicnest.core.data.mapper.toEntity
import com.comicnest.core.data.mapper.toModel
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.RecentVideo
import com.comicnest.core.model.VideoDimensions
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoProgress
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import com.comicnest.source.video.VideoSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 视频仓储实现（DESIGN v2 §3.5）：唯一同时接触 DAO 与 [VideoSource] 的地方。
 * 扫描继承、缩略图回写、删除流水线均在此收口；全部 IO 以 `runCatchingIo` 包成 `Result`。
 */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val source: VideoSource,
    private val rootDao: VideoRootDao,
    private val videoDao: VideoDao,
    private val progressDao: VideoProgressDao,
) : VideoRepository {

    override fun observeRoots(): Flow<List<VideoRoot>> =
        rootDao.observeAll().map { list -> list.map(VideoRootEntity::toModel) }

    override suspend fun addRoot(path: String): Result<VideoRoot> = runCatchingIo {
        val trimmed = path.trim()
        require(trimmed.isNotEmpty()) { "视频库目录不能为空" }
        val file = File(trimmed)
        val normalized = file.absolutePath
        when {
            !file.exists() -> error("目录不存在：$normalized")
            !file.isDirectory -> error("不是文件夹：$normalized")
            !file.canRead() ->
                if (isRestrictedAndroidPath(normalized)) {
                    error(
                        "系统限制：Android 11 及以上无法读取 Android/data、Android/obb 目录，" +
                            "请把视频移动到其他文件夹后再添加。",
                    )
                } else {
                    error("无读取权限：$normalized")
                }
        }
        rootDao.getByPath(normalized)?.let { return@runCatchingIo it.toModel() }
        val root = VideoRoot.of(normalized, file.name.ifBlank { normalized }, System.currentTimeMillis())
        rootDao.insert(root.toEntity())
        root
    }

    override suspend fun removeRoot(rootId: String): Result<Unit> = runCatchingIo {
        val videos = videoDao.getByRoot(rootId)
        videos.forEach { source.deleteThumbnail(it.thumbPath) }
        progressDao.deleteByRoot(rootId)
        videoDao.deleteByRoot(rootId)
        rootDao.deleteById(rootId)
    }

    override fun observeVideos(sort: VideoSortMode): Flow<List<VideoSummary>> {
        val flow = when (sort) {
            VideoSortMode.NAME -> videoDao.observeAll().map { list ->
                list.sortedWith { a, b -> naturalOrderCompare(a.fileName, b.fileName) }
            }
            VideoSortMode.IMPORT_TIME -> videoDao.observeAllByImportedDesc()
        }
        return flow.map { list -> list.map(VideoCacheEntity::toModel) }
    }

    override suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatchingIo {
            var total = 0
            for (rootEntity in rootDao.getAllOnce()) {
                val root = rootEntity.toModel()
                val previous = videoDao.getByRoot(root.id).associateBy { it.videoKeyId }
                val scanResult = source.scanLibrary(root)
                if (scanResult.isFailure) {
                    // 单根失败不阻断其余，也不清空该根旧数据（DESIGN §8）
                    AppLog.w(TAG, "扫描失败，保留旧数据：${root.path}", scanResult.exceptionOrNull())
                    continue
                }
                val merged = scanResult.getOrThrow().map { item ->
                    val prev = previous[item.key.id]
                    inherit(item, prev)
                }
                dropStaleThumbs(previous.values, merged)
                videoDao.replaceRoot(root.id, merged.map(VideoSummary::toEntity))
                total += merged.size
            }
            // 孤儿清理（§3.6）：缓存中已无引用的缩略图，且创建超过 1 天才删
            runCatching { source.cleanupThumbnails(videoDao.getAllThumbPaths().toSet()) }
            total
        }
    }

    /**
     * 继承规则（DESIGN v2 §3.5）：`size` 与 `last_modified` 都没变时，
     * 复用旧缩略图/时长/导入时间；变了则丢弃缩略图重新生成，但保留首次导入时间。
     */
    private fun inherit(item: VideoSummary, prev: VideoCacheEntity?): VideoSummary {
        if (prev == null) return item
        val unchanged = prev.sizeBytes == item.sizeBytes && prev.lastModified == item.lastModified
        val keepThumb = unchanged &&
            prev.thumbState == VideoThumbnailState.READY.ordinal &&
            !prev.thumbPath.isNullOrBlank() &&
            File(prev.thumbPath).isFile
        return item.copy(
            importedAt = prev.importedAt,
            durationMs = if (unchanged) prev.durationMs else null,
            thumbPath = if (keepThumb) prev.thumbPath else null,
            thumbState = if (keepThumb) VideoThumbnailState.READY else VideoThumbnailState.PENDING,
        )
    }

    /** 旧缩略图若已不被任何新条目引用，删除文件（避免缓存只增不减）。 */
    private fun dropStaleThumbs(previous: Collection<VideoCacheEntity>, merged: List<VideoSummary>) {
        val keep = merged.mapNotNullTo(HashSet()) { it.thumbPath }
        previous.forEach { prev ->
            val path = prev.thumbPath
            if (!path.isNullOrBlank() && path !in keep) source.deleteThumbnail(path)
        }
    }

    override suspend fun getVideo(key: VideoKey): Result<VideoSummary> = runCatchingIo {
        videoDao.getById(key.id)?.toModel() ?: error("视频不存在：${key.fileName}")
    }

    override suspend fun generateThumbnail(key: VideoKey): Result<Unit> = runCatchingIo {
        val entity = videoDao.getById(key.id) ?: error("视频不存在：${key.fileName}")
        if (entity.thumbState == VideoThumbnailState.READY.ordinal &&
            !entity.thumbPath.isNullOrBlank() &&
            File(entity.thumbPath).isFile
        ) {
            return@runCatchingIo
        }
        if (entity.thumbState == VideoThumbnailState.FAILED.ordinal) return@runCatchingIo
        if (!File(entity.absPath).isFile) {
            videoDao.updateThumbState(key.id, VideoThumbnailState.FAILED.ordinal)
            error("视频文件不存在：${entity.absPath}")
        }
        val result = source.ensureThumbnail(
            key = key,
            absPath = entity.absPath,
            sizeBytes = entity.sizeBytes,
            lastModified = entity.lastModified,
        ).getOrElse { t ->
            videoDao.updateThumbState(key.id, VideoThumbnailState.FAILED.ordinal)
            throw t
        }
        videoDao.updateThumb(
            keyId = key.id,
            thumbPath = result.path,
            thumbState = VideoThumbnailState.READY.ordinal,
            durationMs = result.durationMs,
            lastModified = entity.lastModified,
        )
    }

    override suspend fun saveThumbnail(
        key: VideoKey,
        path: String,
        durationMs: Long?,
    ): Result<Unit> = runCatchingIo {
        val entity = videoDao.getById(key.id) ?: error("视频不存在：${key.fileName}")
        videoDao.updateThumb(
            keyId = key.id,
            thumbPath = path,
            thumbState = VideoThumbnailState.READY.ordinal,
            durationMs = durationMs,
            lastModified = entity.lastModified,
        )
    }

    override suspend fun markThumbnailFailed(key: VideoKey): Result<Unit> = runCatchingIo {
        videoDao.updateThumbState(key.id, VideoThumbnailState.FAILED.ordinal)
    }

    override suspend fun deleteVideo(key: VideoKey): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingIo {
            val entity = videoDao.getById(key.id) ?: error("视频不存在：${key.fileName}")
            val file = File(entity.absPath)
            if (file.exists()) {
                // 步骤 2：等待同进程正在读该文件的读取器结束（DESIGN v2 §11.2 R2）
                source.awaitFileIdle(entity.absPath, DELETE_IDLE_TIMEOUT_MS)
                // 步骤 3：删文件（短重试覆盖句柄延迟释放），失败则完全不动 DB
                if (!deleteWithRetry(file)) {
                    error("删除失败，文件仍被系统占用，请稍后重试")
                }
                // 步骤 4：文件已删，只清 MediaStore 索引（不再触发扫描重新打开文件）
                source.purgeMediaStore(entity.absPath)
            }
            // 步骤 5：清缓存与记录
            source.deleteThumbnail(entity.thumbPath)
            progressDao.deleteById(key.id)
            videoDao.deleteById(key.id)
            // 步骤 6：兜底——行仍在则重扫该根，防“幽灵卡片”
            if (videoDao.getById(key.id) != null) {
                AppLog.w(TAG, "删除后缓存残留，触发重扫兜底：${key.id}")
                refresh()
            }
        }
    }

    /** 删除文件：3 次短重试，覆盖播放器/媒体库句柄释放延迟；成功或文件消失即返回 true。 */
    private suspend fun deleteWithRetry(file: File): Boolean {
        repeat(DELETE_ATTEMPTS) { attempt ->
            if (runCatching { file.delete() }.getOrDefault(false) || !file.exists()) return true
            DELETE_RETRY_DELAYS_MS.getOrNull(attempt)?.let { delay(it) }
        }
        return false
    }

    override suspend fun readDimensions(key: VideoKey): Result<VideoDimensions> = runCatchingIo {
        val entity = videoDao.getById(key.id) ?: error("视频不存在：${key.fileName}")
        val meta = source.readMeta(entity.absPath).getOrThrow()
        VideoDimensions(
            width = meta.width,
            height = meta.height,
            rotationDegrees = meta.rotationDegrees,
        )
    }

    override suspend fun renameVideo(key: VideoKey, newFileName: String): Result<VideoKey> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                val entity = videoDao.getById(key.id) ?: error("视频不存在：${key.fileName}")
                if (newFileName == key.fileName) return@runCatchingIo key
                val oldFile = File(entity.absPath)
                if (!oldFile.isFile) error("视频文件不存在：${entity.absPath}")
                val parent = oldFile.parentFile ?: error("无法解析视频所在目录")
                val newFile = File(parent, newFileName)
                val oldPath = oldFile.absolutePath
                val newPath = newFile.absolutePath
                if (newFile.exists() && !newPath.equals(oldPath, ignoreCase = true)) {
                    error("同目录已存在同名文件：$newFileName")
                }
                val newKey = VideoKey(key.rootId, newFileName)
                if (videoDao.getById(newKey.id) != null) error("同名视频已存在：$newFileName")

                // 1) 先改磁盘文件：失败则完全不动 DB
                source.renameFile(oldPath, newPath).getOrThrow()
                // 2) 迁移缩略图缓存（改名不重新解码；失败则退化为重新生成）
                val newThumbPath = source.moveThumbnail(
                    oldPath = entity.thumbPath,
                    newKey = newKey,
                    sizeBytes = entity.sizeBytes,
                    lastModified = entity.lastModified,
                )
                val renamed = entity.copy(
                    videoKeyId = newKey.id,
                    fileName = newFileName,
                    absPath = newPath,
                    thumbPath = newThumbPath,
                    thumbState = thumbStateAfterRename(entity, newThumbPath),
                )
                // 3) 单事务替换缓存主键 + 迁移观看记录；失败则把文件名与缩略图回滚
                try {
                    videoDao.renameCache(key.id, renamed)
                } catch (t: Throwable) {
                    runCatching { source.renameFile(newPath, oldPath) }
                    if (newThumbPath != null) {
                        source.moveThumbnail(newThumbPath, key, entity.sizeBytes, entity.lastModified)
                    }
                    throw t
                }
                newKey
            }
        }

    /** 缩略图迁移成功沿用原状态；原先有图但迁移失败则重回 PENDING 等待重生成。 */
    private fun thumbStateAfterRename(entity: VideoCacheEntity, newThumbPath: String?): Int = when {
        newThumbPath != null -> entity.thumbState
        entity.thumbPath.isNullOrBlank() -> entity.thumbState
        else -> VideoThumbnailState.PENDING.ordinal
    }

    override suspend fun saveProgress(
        key: VideoKey,
        positionMs: Long,
        durationMs: Long,
    ): Result<Unit> = runCatchingIo {
        progressDao.saveAndEvict(
            VideoProgressEntity(
                videoKeyId = key.id,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            ),
            LRU_KEEP,
        )
    }

    override suspend fun getProgress(key: VideoKey): VideoProgress? =
        runCatching { progressDao.getById(key.id)?.toModel() }.getOrNull()

    override fun observeRecent(): Flow<List<RecentVideo>> =
        combine(
            progressDao.observeRecent(LRU_KEEP),
            videoDao.observeAll(),
        ) { progress, videos ->
            val byId = videos.associateBy { it.videoKeyId }
            progress.mapNotNull { p ->
                byId[p.videoKeyId]?.let { RecentVideo(p.toModel(), it.toModel()) }
            }
        }

    companion object {
        private const val TAG = "VideoRepository"
        const val LRU_KEEP = 3

        /** 删除前的读取器等待上限（DESIGN v2 §11.2 R2）。 */
        private const val DELETE_IDLE_TIMEOUT_MS = 1_500L
        private const val DELETE_ATTEMPTS = 3
        private val DELETE_RETRY_DELAYS_MS = longArrayOf(60L, 160L)
    }
}

/** Android 11+ 系统级限制目录（即便有「所有文件访问权限」也读不到）。 */
private fun isRestrictedAndroidPath(path: String): Boolean =
    path.contains("/Android/data/") || path.contains("/Android/obb/")
