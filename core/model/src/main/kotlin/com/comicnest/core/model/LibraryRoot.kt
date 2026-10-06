package com.comicnest.core.model

import java.security.MessageDigest

/**
 * 一个导入的库根目录（D2：绝对路径访问）。
 * [id] 为 [path] 的稳定哈希，作为 ComicKey.rootId 使用，避免路径直接暴露/长度限制。
 */
data class LibraryRoot(
    val id: String,
    val path: String,
    val displayName: String,
    val addedAt: Long,
) {
    companion object {
        /** 以绝对路径构造库根，[id] 由路径稳定哈希得到。 */
        fun of(path: String, displayName: String, addedAt: Long): LibraryRoot =
            LibraryRoot(
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
