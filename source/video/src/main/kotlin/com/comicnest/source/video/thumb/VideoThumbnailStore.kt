package com.comicnest.source.video.thumb

import com.comicnest.core.common.util.md5Hex
import java.io.File

/**
 * 缩略图缓存目录（DESIGN v2 §3.6）：`filesDir/video_thumbs/`。
 *
 * 缓存键 = `md5Hex("$videoKeyId|$sizeBytes|$lastModified")`，文件被同名替换/重新转码后
 * `size` 或 `lastModified` 变化 → 键变化 → 自动失效重生成。
 */
class VideoThumbnailStore(private val dir: File) {

    /** 该视频对应的缓存文件（不保证存在）。 */
    fun pathFor(videoKeyId: String, sizeBytes: Long, lastModified: Long): File =
        File(dir, md5Hex("$videoKeyId|$sizeBytes|$lastModified") + ".jpg")

    val directory: File get() = dir

    /** 原子写入：先写 `.tmp` 再 rename，避免半张图被读到。 */
    fun write(target: File, bytes: ByteArray): File {
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // rename 失败（跨卷/占用）时退化为直接写
            target.writeBytes(bytes)
            tmp.delete()
        }
        return target
    }

    /**
     * 迁移缓存文件：把 [oldPath] 改名（跨卷时复制）到新 key 对应的缓存路径，返回新绝对路径。
     * 旧路径为空、文件不存在或迁移失败返回 null。
     */
    fun move(
        oldPath: String?,
        videoKeyId: String,
        sizeBytes: Long,
        lastModified: Long,
    ): String? {
        if (oldPath.isNullOrBlank()) return null
        val source = File(oldPath)
        if (!source.isFile) return null
        val target = pathFor(videoKeyId, sizeBytes, lastModified)
        if (target.absolutePath == source.absolutePath) return target.absolutePath
        dir.mkdirs()
        if (target.exists()) target.delete()
        if (runCatching { source.renameTo(target) }.getOrDefault(false)) {
            return target.absolutePath
        }
        // 跨卷等场景退化为“复制 + 删旧”
        return runCatching {
            source.copyTo(target, overwrite = true)
            source.delete()
            target.absolutePath
        }.getOrNull()
    }

    /** 幂等删除。 */
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /**
     * 孤儿清理：缓存目录中不在 [validPaths] 内、且创建时间早于 [olderThanMs] 的文件删除。
     */
    fun cleanupOrphans(
        validPaths: Set<String>,
        olderThanMs: Long = ORPHAN_MIN_AGE_MS,
        now: Long = System.currentTimeMillis(),
    ) {
        val files = runCatching { dir.listFiles()?.toList() }.getOrNull() ?: return
        val validNames = validPaths.mapTo(HashSet()) { File(it).name }
        files.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name in validNames) return@forEach
            if (now - file.lastModified() < olderThanMs) return@forEach
            runCatching { file.delete() }
        }
    }

    /**
     * 容量控制：目录总大小超过 [maxBytes] 时，按 `lastModified` 从旧到新删除，直到低于 [targetBytes]。
     */
    fun enforceCapacity(
        maxBytes: Long = MAX_TOTAL_BYTES,
        targetBytes: Long = TARGET_TOTAL_BYTES,
    ) {
        val files = runCatching { dir.listFiles()?.filter { it.isFile } }.getOrNull() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= targetBytes) return
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) total -= length
        }
    }

    companion object {
        const val MAX_TOTAL_BYTES = 80L * 1024L * 1024L
        const val TARGET_TOTAL_BYTES = 60L * 1024L * 1024L
        const val ORPHAN_MIN_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
