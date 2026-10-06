package com.comicnest.source.local.scan

import com.comicnest.core.common.util.naturalOrderCompare
import java.io.File

/**
 * 目录扫描（两级模型，D1：库根/漫画名/图片，无章节层）。
 * 仅取一级子目录作为漫画，跳过隐藏目录与文件；无有效图片的漫画由上层 [FormatGuard] 语义跳过。
 */
object DirectoryScanner {

    /** 列出库根下的漫画目录（一级、非隐藏、按自然序）。 */
    fun comicDirs(root: File): List<File> =
        root.safeListFiles()
            .filter { it.isDirectory && !it.isHiddenEntry() }
            .sortedWith { a, b -> naturalOrderCompare(a.name, b.name) }

    private fun File.isHiddenEntry(): Boolean = name.startsWith(".") || isHidden()

    private fun File.safeListFiles(): List<File> = listFiles()?.toList() ?: emptyList()
}
