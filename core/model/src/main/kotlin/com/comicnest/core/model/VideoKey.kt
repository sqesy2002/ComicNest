package com.comicnest.core.model

/**
 * 视频唯一键（DESIGN v2 §1.2 原则 6）：`rootId + "::" + fileName`。
 * 跨目录同名文件天然不冲突；同一目录内文件名不可重复。
 */
data class VideoKey(
    val rootId: String,
    val fileName: String,
) {
    /** 内部唯一键（DB 主键 / 路由参数）。 */
    val id: String get() = "$rootId::$fileName"

    /** 展示名（去扩展名后的标题）。 */
    val displayName: String get() = titleOf(fileName)

    companion object {
        /** 由 [id] 反解；格式非法返回 null（防御路由参数被篡改）。 */
        fun fromId(id: String): VideoKey? {
            val idx = id.indexOf("::")
            if (idx <= 0) return null
            val fileName = id.substring(idx + 2)
            if (fileName.isEmpty()) return null
            return VideoKey(id.substring(0, idx), fileName)
        }

        /** 去掉最后一个扩展名，保留原始大小写。 */
        fun titleOf(fileName: String): String {
            val dot = fileName.lastIndexOf('.')
            return if (dot > 0) fileName.substring(0, dot) else fileName
        }
    }
}
