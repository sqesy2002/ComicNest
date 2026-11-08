package com.comicnest.core.model

/** 缩略图生成状态（DESIGN v2 §3.1）：PENDING 待生成 / READY 已有 / FAILED 失败不再自动重试。 */
enum class VideoThumbnailState {
    PENDING,
    READY,
    FAILED,
}

/**
 * 视频网格项（DESIGN v2 §3.1）。
 *
 * @param durationMs 为 null 表示尚未读取（首次生成缩略图时回填）。
 * @param thumbPath 为 null 表示尚无缩略图缓存。
 */
data class VideoSummary(
    val key: VideoKey,
    val absPath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val importedAt: Long,
    val durationMs: Long?,
    val thumbPath: String?,
    val thumbState: VideoThumbnailState,
) {
    /** 是否可点击进入播放（文件仍在且缩略图未标记为不可用不影响播放）。 */
    val displayName: String get() = key.displayName
}
