package com.comicnest.core.data.repository

import com.comicnest.core.common.result.runCatchingIo
import com.comicnest.core.data.db.dao.ComicDao
import com.comicnest.core.data.db.dao.RootDao
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.db.entity.LibraryRootEntity
import com.comicnest.core.data.mapper.toEntity
import com.comicnest.core.data.mapper.toModel
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.SortMode
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.comicnest.source.api.ComicSource

/**
 * 书架仓储实现：聚合 [ComicSource]（本地扫描）与 Room 缓存。
 * 扫描/IO 全程 `runCatchingIo`，单根失败不影响其余（降级，D11）。
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val source: ComicSource,
    private val rootDao: RootDao,
    private val comicDao: ComicDao,
) : LibraryRepository {

    override fun observeRoots(): Flow<List<LibraryRoot>> =
        rootDao.observeAll().map { list -> list.map(LibraryRootEntity::toModel) }

    override suspend fun addRoot(path: String): Result<LibraryRoot> = runCatchingIo {
        val trimmed = path.trim()
        require(trimmed.isNotEmpty()) { "路径不能为空" }
        val file = File(trimmed)
        val normalized = file.absolutePath
        when {
            !file.exists() -> error("路径不存在：$normalized")
            !file.isDirectory -> error("不是文件夹：$normalized")
            !file.canRead() ->
                if (isRestrictedAndroidPath(normalized))
                    error(
                        "系统限制：Android 11 及以上即使开启「所有文件访问权限」也无法读取 " +
                            "Android/data、Android/obb 目录。请把漫画移动到其他文件夹后再添加。",
                    )
                else error("无读取权限：$normalized")
        }
        val existing = rootDao.getByPath(normalized)
        if (existing != null) return@runCatchingIo existing.toModel()
        val root = LibraryRoot.of(normalized, file.name.ifBlank { normalized }, System.currentTimeMillis())
        rootDao.insert(root.toEntity())
        root
    }

    override suspend fun removeRoot(rootId: String): Result<Unit> = runCatchingIo {
        rootDao.deleteComicsOfRoot(rootId)
        rootDao.deleteById(rootId)
    }

    override fun observeComics(sort: SortMode): Flow<List<ComicSummary>> =
        when (sort) {
            SortMode.NAME -> comicDao.observeByName()
            SortMode.IMPORT_TIME -> comicDao.observeByImportTime()
        }.map { list -> list.map(ComicCacheEntity::toModel) }

    override suspend fun refresh(): Result<Int> = runCatchingIo {
        val roots = rootDao.observeAll().first().map { it.toModel() }
        val merged = mutableListOf<ComicSummary>()
        for (root in roots) {
            // 单个库根扫描失败则跳过（不阻断整体，D11）
            source.scanLibrary(root).getOrNull()?.let { merged += it }
        }
        comicDao.replaceAll(merged.map(ComicSummary::toEntity))
        merged.size
    }

    override suspend fun getComic(key: ComicKey): Result<ComicSummary> = runCatchingIo {
        comicDao.getById(key.id)?.toModel() ?: error("漫画不存在：${key.id}")
    }

    override suspend fun getPages(key: ComicKey): Result<List<ComicPageRef>> = runCatchingIo {
        val rootEntity = rootDao.getById(key.rootId) ?: error("库根不存在：${key.rootId}")
        source.getPages(key, rootEntity.toModel()).getOrThrow()
    }
}

private fun LibraryRoot.toEntity(): LibraryRootEntity =
    LibraryRootEntity(id = id, path = path, displayName = displayName, addedAt = addedAt)

/** Android 11+ 对 `Android/data`、`Android/obb` 的系统级访问限制区（即便有所有文件访问权限也被屏蔽）。 */
private fun isRestrictedAndroidPath(path: String): Boolean =
    path.contains("/Android/data/") || path.contains("/Android/obb/")
