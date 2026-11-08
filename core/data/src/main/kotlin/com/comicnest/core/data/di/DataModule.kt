package com.comicnest.core.data.di

import android.content.Context
import androidx.room.Room
import com.comicnest.core.data.db.ComicNestDatabase
import com.comicnest.core.data.db.VideoMigrations
import com.comicnest.core.data.db.dao.CollectionDao
import com.comicnest.core.data.db.dao.ComicDao
import com.comicnest.core.data.db.dao.ProgressDao
import com.comicnest.core.data.db.dao.RootDao
import com.comicnest.core.data.db.dao.VideoDao
import com.comicnest.core.data.db.dao.VideoProgressDao
import com.comicnest.core.data.db.dao.VideoRootDao
import com.comicnest.core.data.prefs.PreferencesRepositoryImpl
import com.comicnest.core.data.repository.CollectionRepositoryImpl
import com.comicnest.core.data.repository.LibraryRepositoryImpl
import com.comicnest.core.data.repository.ProgressRepositoryImpl
import com.comicnest.core.data.repository.VideoRepositoryImpl
import com.comicnest.core.domain.repository.CollectionRepository
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import com.comicnest.core.domain.repository.ProgressRepository
import com.comicnest.core.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 数据库与 DAO 提供。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ComicNestDatabase =
        Room.databaseBuilder(context, ComicNestDatabase::class.java, "comicnest.db")
            // 只做显式迁移，禁用 destructive（DESIGN v2 §3.5，漫画数据不可丢）
            .addMigrations(*VideoMigrations.ALL)
            .build()

    @Provides
    fun provideRootDao(db: ComicNestDatabase): RootDao = db.rootDao()

    @Provides
    fun provideComicDao(db: ComicNestDatabase): ComicDao = db.comicDao()

    @Provides
    fun provideCollectionDao(db: ComicNestDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun provideProgressDao(db: ComicNestDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideVideoRootDao(db: ComicNestDatabase): VideoRootDao = db.videoRootDao()

    @Provides
    fun provideVideoDao(db: ComicNestDatabase): VideoDao = db.videoDao()

    @Provides
    fun provideVideoProgressDao(db: ComicNestDatabase): VideoProgressDao = db.videoProgressDao()
}

/** 仓储接口 → 实现绑定。`LibraryRepositoryImpl` 依赖的 `ComicSource` 由 `:source:local`（S3）提供绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
