package com.comicnest.core.common.util

/** 受支持的静态/动态图片扩展名白名单（jpg/jpeg/png/webp/gif/bmp + 可选 avif/heif）。 */
private val SUPPORTED_IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heif")

/** 需要以动图解码器播放的扩展名（gif）。animated webp 判定入口预留于此。 */
private val ANIMATED_EXT = setOf("gif")

/** 取文件扩展名（小写，无点）；无扩展名返回空串。 */
fun extensionOf(name: String): String {
    val slash = name.lastIndexOfAny(charArrayOf('/', '\\'))
    val dot = name.lastIndexOf('.')
    // 扩展名的点必须在最后一级文件名内
    if (dot < 0 || dot <= slash || dot == name.length - 1) return ""
    return name.substring(dot + 1).lowercase()
}

/** 是否为受支持的图片文件（按扩展名判定，AC6 六格式）。 */
fun isSupportedImage(name: String): Boolean = extensionOf(name) in SUPPORTED_IMAGE_EXT

/** 是否为动图扩展名（gif，D8）。 */
fun isAnimatedExt(name: String): Boolean = extensionOf(name) in ANIMATED_EXT
