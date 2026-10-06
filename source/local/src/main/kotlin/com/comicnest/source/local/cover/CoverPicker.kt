package com.comicnest.source.local.cover

import com.comicnest.core.common.util.isSupportedImage
import com.comicnest.core.common.util.naturalOrderCompare
import java.io.File

/**
 * 封面/页图片选取（D3：封面=按自然序最小名的受支持图片）。
 * 仅统计扩展名受支持的图片，跳过隐藏文件；无有效图片时列表为空（D11，交由上层跳过该漫画）。
 */
object CoverPicker {

    /** 目录内受支持的图片，按自然序（文件名）升序。 */
    fun supportedImages(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.startsWith(".") && isSupportedImage(it.name) }
            .sortedWith { a, b -> naturalOrderCompare(a.name, b.name) }

    /** 封面路径：自然序最小者；无图返回 null。 */
    fun pickCover(dir: File): String? = supportedImages(dir).firstOrNull()?.absolutePath
}
