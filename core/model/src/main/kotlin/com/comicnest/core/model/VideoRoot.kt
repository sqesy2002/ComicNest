package com.comicnest.core.model

import java.security.MessageDigest

/**
 * 一个导入的视频库根目录（DESIGN v2 V2/V11）。
 * 与漫画库根独立存表，[id] 为 [path] 的稳定哈希，作为 [VideoKey.rootId] 使用。
 */
data class VideoRoot(
    val id: String,
    val path: String,
    val displayName: String,
    val addedAt: Long,
) {
    companion object {
        fun of(path: String, displayName: String, addedAt: Long): VideoRoot =
            VideoRoot(
                id = makeId(path),
                path = path,
                displayName = displayName,
                addedAt = addedAt,
            )

        /** 稳定哈希：SHA-256 前 16 位十六进制；同一路径恒得同一 id。 */
        fun makeId(path: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
        }
    }
}
