package com.comicnest.core.common.util

import java.security.MessageDigest

/** 受支持的视频容器扩展名白名单（DESIGN v2 V9：mp4/m4v/mkv/webm/ts/mov/3gp）。 */
private val SUPPORTED_VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "ts", "mov", "3gp")

/** 是否为受支持的视频文件（按扩展名判定，白名单外不入库、不展示）。 */
fun isSupportedVideo(name: String): Boolean = extensionOf(name) in SUPPORTED_VIDEO_EXT

/** 受支持视频扩展名集合（供 UI/提示文案与测试复用）。 */
val supportedVideoExtensions: Set<String> get() = SUPPORTED_VIDEO_EXT

/**
 * 时长格式化：null/非正 → `--:--`；<1h → `mm:ss`；>=1h → `h:mm:ss`。
 * 用于卡片角标与播放器时间轴。
 */
fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** 字节数格式化（保留一位小数，仅在需要处使用）。 */
fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "--"
    bytes < 1024L -> "${bytes} B"
    bytes < 1024L * 1024L -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 32 位 MD5 十六进制（缩略图缓存键，DESIGN v2 §3.6）。 */
fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
