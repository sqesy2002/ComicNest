package com.comicnest.core.model

/**
 * 阅读进度（D6：LRU=5）。[comicKeyId] 为 [ComicKey.id]。
 * 保存时以 [updatedAt] 维护最近 5 条，超出淘汰最旧。
 */
data class ReadProgress(
    val comicKeyId: String,
    val pageIndex: Int,
    val totalPages: Int,
    val updatedAt: Long,
)
