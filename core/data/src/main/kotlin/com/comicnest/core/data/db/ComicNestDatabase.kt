package com.comicnest.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.comicnest.core.data.db.dao.ComicDao
import com.comicnest.core.data.db.dao.CollectionDao
import com.comicnest.core.data.db.dao.ProgressDao
import com.comicnest.core.data.db.dao.RootDao
import com.comicnest.core.data.db.entity.CollectionEntity
import com.comicnest.core.data.db.entity.CollectionEntryEntity
import com.comicnest.core.data.db.entity.ComicCacheEntity
import com.comicnest.core.data.db.entity.LibraryRootEntity
import com.comicnest.core.data.db.entity.ReadProgressEntity

@Database(
    entities = [
        LibraryRootEntity::class,
        ComicCacheEntity::class,
        CollectionEntity::class,
        CollectionEntryEntity::class,
        ReadProgressEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ComicNestDatabase : RoomDatabase() {
    abstract fun rootDao(): RootDao
    abstract fun comicDao(): ComicDao
    abstract fun collectionDao(): CollectionDao
    abstract fun progressDao(): ProgressDao
}
