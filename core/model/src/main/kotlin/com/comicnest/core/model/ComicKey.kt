package com.comicnest.core.model

/**
 * 漫画内部唯一键（D10）：`rootId + "::" + comicName`。
 * 展示名仅为 [comicName]，用于解决不同库根下的重名漫画并存互不干扰（AC）。
 */
data class ComicKey(
    val rootId: String,
    val comicName: String,
) {
    /** 内部唯一标识，作为缓存/进度/收藏的关联键。 */
    val id: String = "$rootId::$comicName"

    /** 用户可见名称。 */
    val displayName: String = comicName

    companion object {
        /** 从内部 [id] 反解为 ComicKey；无分隔符时视为解析失败，rootId 为空。 */
        fun fromId(id: String): ComicKey {
            val idx = id.indexOf("::")
            return if (idx < 0) {
                ComicKey(rootId = "", comicName = id)
            } else {
                ComicKey(rootId = id.substring(0, idx), comicName = id.substring(idx + 2))
            }
        }
    }
}
