package com.comicnest.di

import com.comicnest.source.api.ComicSource
import com.comicnest.source.local.LocalComicSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 来源装配（app 作为组装层，DESIGN §2）：将 [ComicSource] 绑定到本期唯一实现 [LocalComicSource]。
 * 未来接入联网来源时，在此注册新实现即可，业务层零改动（AC13）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideComicSource(): ComicSource = LocalComicSource()
}
