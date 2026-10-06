package com.comicnest.core.ui.image

import android.content.Context
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Coil 全局 [ImageLoader] 配置（DESIGN §3.6）：内存/磁盘缓存 + gif 动图解码（D8）。
 * [GifDecoder] 在 API 28+ 内部走 `ImageDecoder`、低版本回退，兼容 minSdk 26。
 */
fun createComicImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(GifDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(context).maxSizePercent(0.25).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .build()
