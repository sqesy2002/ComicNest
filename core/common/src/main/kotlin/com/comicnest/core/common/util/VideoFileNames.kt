package com.comicnest.core.common.util

/**
 * 视频重命名规则（DESIGN v2 追加）：只允许修改主名，扩展名固定保留，
 * 以保证扫描白名单、播放器识别与缩略图缓存键始终可用。
 */
object VideoFileNames {

    /** 主名最大字符数（UTF-8 中文约 3 字节/字，60 字远低于文件系统 255 字节上限）。 */
    const val MAX_BASE_LENGTH = 60

    /** 各文件系统共用的非法字符（FAT/exFAT 额外禁止 `:` `*` `?` 等）。 */
    private const val INVALID_CHARS = "/\\:*?\"<>|"

    /** 去掉最后一个扩展名（点号在首位时不视为扩展名）。 */
    fun baseNameOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    /** 最后一个扩展名（不含点号）；无扩展名返回空串。 */
    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0 && dot < fileName.length - 1) fileName.substring(dot + 1) else ""
    }

    /**
     * 校验并规范化用户输入的主名：去首尾空白，拒绝空/超长/非法字符/纯点。
     * 失败时 `message` 可直接展示给用户。
     */
    fun validateBaseName(raw: String): Result<String> {
        val name = raw.trim()
        if (name.isEmpty()) return failure("名称不能为空")
        if (name.length > MAX_BASE_LENGTH) {
            return failure("名称最多 ${MAX_BASE_LENGTH} 个字符")
        }
        if (name.any { it in INVALID_CHARS || it.isISOControl() }) {
            return failure("名称不能包含 / \\ : * ? \" < > | 等特殊字符")
        }
        if (name == "." || name == "..") return failure("名称无效")
        if (name.endsWith('.')) return failure("名称不能以点结尾")
        return Result.success(name)
    }

    /** 用新主名 + 原扩展名拼出完整文件名。 */
    fun fileNameFor(baseName: String, oldFileName: String): String {
        val extension = extensionOf(oldFileName)
        return if (extension.isEmpty()) baseName else "${baseName}.${extension}"
    }

    private fun failure(message: String): Result<String> =
        Result.failure(IllegalArgumentException(message))
}
