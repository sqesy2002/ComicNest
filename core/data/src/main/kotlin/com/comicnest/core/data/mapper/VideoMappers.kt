package com.comicnest.core.data.mapper

import com.comicnest.core.data.db.entity.VideoCacheEntity
import com.comicnest.core.data.db.entity.VideoProgressEntity
import com.comicnest.core.data.db.entity.VideoRootEntity
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoProgress
import com.comicnest.core.model.VideoRoot
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState

fun VideoRootEntity.toModel(): VideoRoot =
    VideoRoot(id = id, path = path, displayName = displayName, addedAt = addedAt)

fun VideoRoot.toEntity(): VideoRootEntity =
    VideoRootEntity(id = id, path = path, displayName = displayName, addedAt = addedAt)

fun VideoCacheEntity.toModel(): VideoSummary =
    VideoSummary(
        key = VideoKey(rootId = rootId, fileName = fileName),
        absPath = absPath,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        importedAt = importedAt,
        durationMs = durationMs,
        thumbPath = thumbPath,
        thumbState = thumbStateFrom(thumbState),
    )

fun VideoSummary.toEntity(): VideoCacheEntity =
    VideoCacheEntity(
        videoKeyId = key.id,
        rootId = key.rootId,
        fileName = key.fileName,
        absPath = absPath,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        importedAt = importedAt,
        durationMs = durationMs,
        thumbPath = thumbPath,
        thumbState = thumbState.ordinal,
    )

fun VideoProgressEntity.toModel(): VideoProgress =
    VideoProgress(
        videoKeyId = videoKeyId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )

fun VideoProgress.toEntity(): VideoProgressEntity =
    VideoProgressEntity(
        videoKeyId = videoKeyId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )

/** 越界 ordinal 回退 PENDING（防御历史脏数据）。 */
private fun thumbStateFrom(ordinal: Int): VideoThumbnailState =
    VideoThumbnailState.entries.getOrNull(ordinal) ?: VideoThumbnailState.PENDING
