package com.comicnest.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.comicnest.core.data.db.dao.CollectionDao
import com.comicnest.core.data.db.dao.ComicDao
import com.comicnest.core.data.db.dao.ProgressDao
import com.comicnest.core.data.db.dao.RootDao
import com.comicnest.core.data.db.dao.VideoDao
import com.comicnest.core.data.db.dao.VideoProgressDao
import com.comicnest.core.data.db.dao.VideoRootDao
import com.comicnest.core.data.db.entity.CollectionEntity
import com.comicnest.core.data.db.entity.CollectionEntryEntity
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.db.entity.LibraryRootEntity
import com.comicnest.core.data.db.entity.ReadProgressEntity
import com.comicnest.core.data.db.entity.VideoCacheEntity
import com.comicnest.core.data.db.entity.VideoProgressEntity
import com.comicnest.core.data.db.entity.VideoRootEntity

/**
 * 单库双域（DESIGN v2 §3.5）：v1 五张漫画表 + v2 三张视频表。
 * 版本升级只允许通过 [VideoMigrations] 显式迁移（禁止 destructive）。
 */
@Database(
    entities = [
        LibraryRootEntity::class,
        ComicCacheEntity::class,
        CollectionEntity::class,
        CollectionEntryEntity::class,
        ReadProgressEntity::class,
        VideoRootEntity::class,
        VideoCacheEntity::class,
        VideoProgressEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ComicNestDatabase : RoomDatabase() {
    abstract fun rootDao(): RootDao
    abstract fun comicDao(): ComicDao
    abstract fun collectionDao(): CollectionDao
    abstract fun progressDao(): ProgressDao
    abstract fun videoRootDao(): VideoRootDao
    abstract fun videoDao(): VideoDao
    abstract fun videoProgressDao(): VideoProgressDao
}
