package com.comicnest.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 视频库根表（DESIGN v2 §3.5）。path UNIQUE，同路径不重复导入。 */
@Entity(
    tableName = "video_root",
    indices = [Index(value = ["path"], unique = true)],
)
data class VideoRootEntity(
    @PrimaryKey val id: String,
    val path: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * 视频缓存表（refresh 时按 root 整表替换）。
 * `thumb_state` 存 [com.comicnest.core.model.VideoThumbnailState] 的 ordinal。
 */
@Entity(
    tableName = "video_cache",
    indices = [Index("root_id")],
)
data class VideoCacheEntity(
    @PrimaryKey @ColumnInfo(name = "video_key_id") val videoKeyId: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "abs_path") val absPath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "thumb_path") val thumbPath: String?,
    @ColumnInfo(name = "thumb_state") val thumbState: Int,
)

/** 观看记录表（保存时保证行数 ≤3，LRU）。 */
@Entity(tableName = "video_progress")
data class VideoProgressEntity(
    @PrimaryKey @ColumnInfo(name = "video_key_id") val videoKeyId: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
