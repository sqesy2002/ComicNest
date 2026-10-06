package com.comicnest.core.model

/**
 * 主页网格项：一部漫画的摘要（D3：封面=按名最小图）。
 * [coverPath] 为 null 表示目录内无受支持图片，UI 显示占位图。
 */
data class ComicSummary(
    val key: ComicKey,
    val coverPath: String?,
    val imageCount: Int,
    val importedAt: Long,
    val lastModified: Long,
)
