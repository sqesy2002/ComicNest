package com.comicnest.core.domain.repository

import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.SortMode
import kotlinx.coroutines.flow.Flow

/** 书架/漫画库数据访问契约（由 `:core:data` 实现，聚合扫描源 + DB 缓存）。 */
interface LibraryRepository {

    /** 观察已导入库根。 */
    fun observeRoots(): Flow<List<LibraryRoot>>

    /** 校验目录存在/可读后新增库根。 */
    suspend fun addRoot(path: String): Result<LibraryRoot>

    /** 移除库根（连带失效其漫画缓存）。 */
    suspend fun removeRoot(rootId: String): Result<Unit>

    /** 观察漫画总览（按排序）。 */
    fun observeComics(sort: SortMode): Flow<List<ComicSummary>>

    /** 触发全量重扫，返回漫画数；写缓存。 */
    suspend fun refresh(): Result<Int>

    /** 单部详情。 */
    suspend fun getComic(key: ComicKey): Result<ComicSummary>

    /** 该漫画自然序页列表。 */
    suspend fun getPages(key: ComicKey): Result<List<ComicPageRef>>
}
