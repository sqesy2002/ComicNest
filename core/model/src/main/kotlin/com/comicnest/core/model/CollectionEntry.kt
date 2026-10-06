package com.comicnest.core.model

/** 收藏夹与漫画的多对多关联（D7）。[comicKeyId] 为 [ComicKey.id]。 */
data class CollectionEntry(
    val collectionId: Long,
    val comicKeyId: String,
)
