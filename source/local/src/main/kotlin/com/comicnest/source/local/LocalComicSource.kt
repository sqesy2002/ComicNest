package com.comicnest.source.local

import com.comicnest.core.common.result.runCatchingIo
import com.comicnest.core.common.util.isAnimatedExt
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.source.api.ComicSource
import com.comicnest.source.local.cover.CoverPicker
import com.comicnest.source.local.scan.DirectoryScanner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地目录来源（唯一实现，两级模型 D1；绝对路径访问 D2）。
 *
 * - [scanLibrary]：库根一级子目录=漫画；封面=自然序最小图（D3）；无有效图的漫画跳过（D11）。
 * - [getPages]：漫画目录内受支持图片按自然序成页，标记 gif 动图（D8）。
 *
 * 全程只读用户文件；IO 于 [Dispatchers.IO]，异常以 `Result.failure` 返回，不抛裸异常。
 */
class LocalComicSource : ComicSource {

    override val id: String = "local"
    override val label: String = "本地目录"

    override suspend fun scanLibrary(root: LibraryRoot): Result<List<ComicSummary>> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                val rootDir = File(root.path)
                if (!rootDir.isDirectory) {
                    error("库根不存在或不是目录：${root.path}")
                }
                val now = System.currentTimeMillis()
                DirectoryScanner.comicDirs(rootDir).mapNotNull { dir ->
                    val images = CoverPicker.supportedImages(dir)
                    if (images.isEmpty()) {
                        null // 空/无有效图 → 跳过（D11）
                    } else {
                        ComicSummary(
                            key = ComicKey(rootId = root.id, comicName = dir.name),
                            coverPath = images.first().absolutePath,
                            imageCount = images.size,
                            importedAt = now,
                            lastModified = dir.lastModified(),
                        )
                    }
                }
            }
        }

    override suspend fun getPages(key: ComicKey, root: LibraryRoot): Result<List<ComicPageRef>> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                val dir = File(root.path, key.comicName)
                if (!dir.isDirectory) {
                    error("漫画目录不存在：${dir.path}")
                }
                CoverPicker.supportedImages(dir).mapIndexed { index, file ->
                    ComicPageRef(
                        comicKey = key,
                        index = index,
                        fileName = file.name,
                        absPath = file.absolutePath,
                        isAnimated = isAnimatedExt(file.name),
                    )
                }
            }
        }
}
