package com.comicnest.source.video.di

import com.comicnest.source.video.LocalVideoSource
import com.comicnest.source.video.VideoSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 视频来源装配（DESIGN v2 §3.4）：本期唯一实现 [LocalVideoSource]。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VideoSourceModule {

    @Binds
    @Singleton
    abstract fun bindVideoSource(impl: LocalVideoSource): VideoSource
}
