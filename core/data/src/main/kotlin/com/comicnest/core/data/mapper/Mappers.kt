package com.comicnest.core.data.mapper

import com.comicnest.core.data.db.entity.CollectionEntity
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.db.entity.LibraryRootEntity
import com.comicnest.core.data.db.entity.ReadProgressEntity
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReadProgress

fun LibraryRootEntity.toModel(): LibraryRoot =
    LibraryRoot(id = id, path = path, displayName = displayName, addedAt = addedAt)

fun ComicCacheEntity.toModel(): ComicSummary =
    ComicSummary(
        key = ComicKey(rootId = rootId, comicName = comicName),
        coverPath = coverPath,
        imageCount = imageCount,
        importedAt = importedAt,
        lastModified = lastModified,
    )

fun ComicSummary.toEntity(): ComicCacheEntity =
    ComicCacheEntity(
        comicKeyId = key.id,
        rootId = key.rootId,
        comicName = key.comicName,
        coverPath = coverPath,
        imageCount = imageCount,
        importedAt = importedAt,
        lastModified = lastModified,
    )

fun CollectionEntity.toModel(): Collection =
    Collection(id = id, name = name, createdAt = createdAt)

fun ReadProgressEntity.toModel(): ReadProgress =
    ReadProgress(comicKeyId = comicKeyId, pageIndex = pageIndex, totalPages = totalPages, updatedAt = updatedAt)

fun ReadProgress.toEntity(): ReadProgressEntity =
    ReadProgressEntity(
        comicKeyId = comicKeyId,
        pageIndex = pageIndex,
        totalPages = totalPages,
        updatedAt = updatedAt,
    )
