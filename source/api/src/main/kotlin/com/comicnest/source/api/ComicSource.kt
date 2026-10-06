package com.comicnest.source.api

import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot

/**
 * 漫画来源抽象（扩展点，PRD-AC13）。本期唯一实现 [LocalComicSource 占位] 于 `:source:local`；
 * 未来联网检索只需新增实现并注册，UI/用例层零改动。
 *
 * 约定：所有 IO 在实现内切 `Dispatchers.IO`，以 `Result` 返回，不抛裸异常（D11）。
 */
interface ComicSource {

    /** 来源唯一标识（本地为 "local"）。 */
    val id: String

    /** 展示名（"本地目录"）。 */
    val label: String

    /** 扫描一个库根，产出漫画列表（含封面=按名最小图，D3）。 */
    suspend fun scanLibrary(root: LibraryRoot): Result<List<ComicSummary>>

    /** 产出某漫画的自然序页列表（AC4）。 */
    suspend fun getPages(key: ComicKey, root: LibraryRoot): Result<List<ComicPageRef>>

    /** 联网检索预留；本地实现默认返回空（不实现具体检索）。 */
    suspend fun search(query: String, page: Int): Result<List<ComicSummary>> =
        Result.success(emptyList())
}
