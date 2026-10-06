package com.comicnest.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 库根表（DESIGN §3.5）。path UNIQUE。 */
@Entity(
    tableName = "library_root",
    indices = [Index(value = ["path"], unique = true)],
)
data class LibraryRootEntity(
    @PrimaryKey val id: String,
    val path: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** 漫画缓存表（refresh 时整表重建）。rootId 索引。 */
@Entity(
    tableName = "comic_cache",
    indices = [Index("root_id")],
)
data class ComicCacheEntity(
    @PrimaryKey @ColumnInfo(name = "comic_key_id") val comicKeyId: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    @ColumnInfo(name = "comic_name") val comicName: String,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "image_count") val imageCount: Int,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
)

/** 收藏夹表。name UNIQUE。 */
@Entity(
    tableName = "collection",
    indices = [Index(value = ["name"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** 收藏关联表（多对多，D7）。级联删除收藏夹时连带清理 entry。 */
@Entity(
    tableName = "collection_entry",
    primaryKeys = ["collection_id", "comic_key_id"],
    indices = [Index("comic_key_id")],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CollectionEntryEntity(
    @ColumnInfo(name = "collection_id") val collectionId: Long,
    @ColumnInfo(name = "comic_key_id") val comicKeyId: String,
)

/** 阅读进度表（保存时保证行数 ≤5，LRU=D6）。 */
@Entity(tableName = "read_progress")
data class ReadProgressEntity(
    @PrimaryKey @ColumnInfo(name = "comic_key_id") val comicKeyId: String,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "total_pages") val totalPages: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
