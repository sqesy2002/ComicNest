package com.comicnest.core.model

/**
 * 观看记录（DESIGN v2 V8）：最多 3 条，第 4 条覆盖最早。
 * [positionMs] 为上次播放位置，用于“继续观看”续播。
 */
data class VideoProgress(
    val videoKeyId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    /** 观看进度比例 [0,1]（供继续观看卡片底部进度条）。 */
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** 剩余时长（毫秒）。 */
    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)
}

/** “继续观看”条目：进度 + 视频摘要（摘要已被删除时由 repository 过滤）。 */
data class RecentVideo(
    val progress: VideoProgress,
    val summary: VideoSummary,
)

/** 视频页排序方式（V12，默认名称自然序）。 */
enum class VideoSortMode {
    NAME,
    IMPORT_TIME,
}
