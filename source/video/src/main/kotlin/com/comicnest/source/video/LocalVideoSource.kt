package com.comicnest.source.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.comicnest.core.common.log.AppLog
import com.comicnest.core.common.result.runCatchingIo
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSummary
import com.comicnest.source.video.meta.VideoMetaReader
import com.comicnest.source.video.scan.VideoScanner
import com.comicnest.source.video.thumb.VideoThumbnailGenerator
import com.comicnest.source.video.thumb.VideoThumbnailStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 本地视频来源（DESIGN v2 §3.4）：只读用户目录，缩略图只写 App 私有目录。
 *
 * - 扫描：只取库根**直接下一级**的视频文件（白名单 + 非隐藏 + 非 0 字节）。
 * - 缩略图：取中间帧 →（近黑降级）→ 按旋转角摆正 → 宽 480px → JPEG 85 → 原子落盘。
 * - 并发：全局 `Semaphore(3)`，单任务 8s 超时；`MediaMetadataRetriever` 成对 release。
 */
@Singleton
class LocalVideoSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoSource {

    override val id: String = "local-video"
    override val label: String = "本地视频"

    private val store: VideoThumbnailStore by lazy {
        VideoThumbnailStore(File(context.filesDir, THUMB_DIR_NAME))
    }

    private val gate = Semaphore(GENERATE_CONCURRENCY)

    /** 每路径正在读取的引用计数：删除前用 [awaitFileIdle] 等它归零（DESIGN v2 §11.2 R2）。 */
    private val activeReads = ConcurrentHashMap<String, AtomicInteger>()

    override suspend fun scanLibrary(root: VideoRoot): Result<List<VideoSummary>> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                val dir = File(root.path)
                if (!dir.isDirectory) error("视频库目录不存在：${root.path}")
                val now = System.currentTimeMillis()
                VideoScanner.listVideoFiles(dir).map { VideoScanner.toSummary(root, it, now) }
            }
        }

    override suspend fun readMeta(absPath: String): Result<VideoMeta> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                beginRead(absPath)
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(absPath)
                        VideoMetaReader.fromRetriever(retriever) ?: error("无法读取视频元数据")
                    } finally {
                        releaseQuietly(retriever)
                    }
                } finally {
                    endRead(absPath)
                }
            }
        }

    override suspend fun ensureThumbnail(
        key: VideoKey,
        absPath: String,
        sizeBytes: Long,
        lastModified: Long,
    ): Result<ThumbnailResult> = withContext(Dispatchers.IO) {
        gate.withPermit {
            val result = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    Result.success(generateThumbnail(key, absPath, sizeBytes, lastModified))
                } catch (t: Throwable) {
                    // 超时/取消必须向上抛，否则会被误判成“生成失败”并写入 FAILED
                    if (t is CancellationException) throw t
                    AppLog.e(TAG, "缩略图生成失败：${key.id}", t)
                    Result.failure(t)
                }
            }
            result ?: Result.failure(IllegalStateException("缩略图生成超时"))
        }
    }

    override fun deleteThumbnail(path: String?) {
        store.delete(path)
    }

    override fun cleanupThumbnails(validPaths: Collection<String>) {
        store.cleanupOrphans(validPaths.toSet())
    }

    override suspend fun renameFile(oldPath: String, newPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                if (oldPath == newPath) return@runCatchingIo
                val oldFile = File(oldPath)
                if (!oldFile.isFile) error("视频文件不存在：$oldPath")
                val newFile = File(newPath)
                // 仅大小写不同时视为同一文件（大小写不敏感文件系统也能走两步改名）
                if (newFile.exists() &&
                    !newFile.absolutePath.equals(oldFile.absolutePath, ignoreCase = true)
                ) {
                    error("同目录已存在同名文件：${newFile.name}")
                }
                val renamed = runCatching { oldFile.renameTo(newFile) }.getOrDefault(false) ||
                    renameViaTemp(oldFile, newFile)
                if (!renamed || oldFile.exists() || !newFile.isFile) {
                    error("重命名失败，文件可能被占用或目录只读")
                }
                // MediaStore：先清旧索引，再让系统索引新路径（尽力而为，不影响结果）
                purgeMediaStore(oldPath)
                runCatching {
                    MediaScannerConnection.scanFile(context, arrayOf(newPath), null) { _, _ -> }
                }.onFailure { AppLog.w(TAG, "MediaStore 新路径索引失败：$newPath", it) }
            }
        }

    override fun moveThumbnail(
        oldPath: String?,
        newKey: VideoKey,
        sizeBytes: Long,
        lastModified: Long,
    ): String? = store.move(oldPath, newKey.id, sizeBytes, lastModified)

    /** 大小写不敏感文件系统上仅改大小写时，先改到临时名再改到目标名。 */
    private fun renameViaTemp(oldFile: File, newFile: File): Boolean = runCatching {
        val temp = File(oldFile.parentFile, "${newFile.name}.renaming")
        if (temp.exists()) temp.delete()
        if (!oldFile.renameTo(temp)) return false
        if (temp.renameTo(newFile)) return true
        temp.renameTo(oldFile)
        false
    }.getOrDefault(false)

    /** 只删 MediaStore 索引，不做重扫：重扫会异步打开文件，导致紧随的删除失败（DESIGN v2 §11.2 R2）。 */
    override suspend fun purgeMediaStore(absPath: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        runCatching {
            resolver.delete(collection, "${MediaStore.MediaColumns.DATA} = ?", arrayOf(absPath))
        }.onFailure { AppLog.w(TAG, "MediaStore 清理失败：$absPath", it) }
        Unit
    }

    override suspend fun awaitFileIdle(absPath: String, timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            while ((activeReads[absPath]?.get() ?: 0) > 0) delay(READ_IDLE_POLL_MS)
        }
    }

    private fun beginRead(absPath: String) {
        activeReads.getOrPut(absPath) { AtomicInteger() }.incrementAndGet()
    }

    private fun endRead(absPath: String) {
        activeReads[absPath]?.let { counter ->
            if (counter.decrementAndGet() <= 0) activeReads.remove(absPath, counter)
        }
    }

    private fun generateThumbnail(
        key: VideoKey,
        absPath: String,
        sizeBytes: Long,
        lastModified: Long,
    ): ThumbnailResult {
        beginRead(absPath)
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(absPath)
                val meta = VideoMetaReader.fromRetriever(retriever) ?: error("无法读取视频元数据")
                val frame = VideoThumbnailGenerator.frameAtMiddle(retriever, meta.durationMs)
                    ?: error("无法获取中间帧")
                val rotated = VideoThumbnailGenerator.applyRotation(frame, meta.rotationDegrees)
                val scaled = VideoThumbnailGenerator.scaleToWidth(rotated, THUMB_MAX_WIDTH_PX)
                val bytes = VideoThumbnailGenerator.encodeJpeg(scaled)
                val target = store.pathFor(key.id, sizeBytes, lastModified)
                store.write(target, bytes)
                store.enforceCapacity()
                return ThumbnailResult(path = target.absolutePath, durationMs = meta.durationMs)
            } finally {
                releaseQuietly(retriever)
            }
        } finally {
            endRead(absPath)
        }
    }

    private fun releaseQuietly(retriever: MediaMetadataRetriever) {
        runCatching { retriever.release() }
    }

    companion object {
        private const val TAG = "LocalVideoSource"
        private const val THUMB_DIR_NAME = "video_thumbs"
        private const val THUMB_MAX_WIDTH_PX = 480
        private const val GENERATE_CONCURRENCY = 3
        private const val TIMEOUT_MS = 8_000L
        private const val READ_IDLE_POLL_MS = 40L
    }
}
