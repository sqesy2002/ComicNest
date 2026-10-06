package com.comicnest

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.comicnest.core.common.log.AppLog
import com.comicnest.core.common.log.LogDelegate
import com.comicnest.core.common.log.LogLevel
import com.comicnest.core.ui.image.createComicImageLoader
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口（DESIGN §3.7）：初始化全局 Coil [ImageLoader]（gif 动图 D8）与 [AppLog] 落地委托。
 */
@HiltAndroidApp
class ComicNestApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        installLogDelegate()
    }

    override fun newImageLoader(): ImageLoader = createComicImageLoader(this)

    /** 将纯 Kotlin 的 [AppLog] 门面接到 `android.util.Log`；release 关闭 DEBUG 级。 */
    private fun installLogDelegate() {
        val debug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        AppLog.enabled = true
        AppLog.delegate = LogDelegate { level, tag, msg, throwable ->
            val t = throwable
            when (level) {
                LogLevel.DEBUG -> if (debug) Log.d(tag, msg, t)
                LogLevel.INFO -> Log.i(tag, msg, t)
                LogLevel.WARN -> Log.w(tag, msg, t)
                LogLevel.ERROR -> Log.e(tag, msg, t)
            }
        }
    }
}
