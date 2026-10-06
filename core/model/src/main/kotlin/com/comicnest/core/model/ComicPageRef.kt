package com.comicnest.core.model

/**
 * 单页引用（阅读器使用）。[index] 从 0 递增，按自然序排列。
 * [isAnimated] 为 true 时用动图解码器播放（gif，D8）。
 */
data class ComicPageRef(
    val comicKey: ComicKey,
    val index: Int,
    val fileName: String,
    val absPath: String,
    val isAnimated: Boolean,
)
