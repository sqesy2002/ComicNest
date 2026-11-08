package com.comicnest.source.video.scan

import com.comicnest.core.common.util.isSupportedVideo
import com.comicnest.core.common.util.naturalOrderCompare
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import java.io.File

/**
 * 视频库扫描（DESIGN v2 §3.4）：只取目录**直接下一级**的普通文件，不递归。
 * 过滤规则：非隐藏、非 0 字节、扩展名命中白名单（V9）；结果按自然序排列。
 */
object VideoScanner {

    /** 列出目录下的视频文件（一级、非隐藏、非空、白名单、自然序）。 */
    fun listVideoFiles(dir: File): List<File> =
        safeListFiles(dir)
            .filter { it.isFile && !it.isHiddenEntry() && it.length() > 0L && isSupportedVideo(it.name) }
            .sortedWith { a, b -> naturalOrderCompare(a.name, b.name) }

    /** 将文件映射为网格项；缩略图初始为 PENDING，时长待首次生成时回填。 */
    fun toSummary(root: VideoRoot, file: File, importedAt: Long): VideoSummary = VideoSummary(
        key = VideoKey(root.id, file.name),
        absPath = file.absolutePath,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        importedAt = importedAt,
        durationMs = null,
        thumbPath = null,
        thumbState = VideoThumbnailState.PENDING,
    )

    private fun File.isHiddenEntry(): Boolean = name.startsWith(".") || isHidden()

    private fun safeListFiles(dir: File): List<File> =
        runCatching { dir.listFiles()?.toList() }.getOrNull() ?: emptyList()
}
