package com.comicnest.core.common.log

/** 日志级别。 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** 日志落地委托：Android 侧在 Application 注入基于 `android.util.Log` 的实现。 */
fun interface LogDelegate {
    fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable?)
}

/**
 * 统一日志门面（纯 Kotlin，无 Android 依赖，便于 common/domain 复用与单测）。
 * 默认无委托即静默丢弃；release 可置 [enabled]=false 关闭。
 */
object AppLog {

    @Volatile
    var delegate: LogDelegate? = null

    @Volatile
    var enabled: Boolean = true

    fun d(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.DEBUG, tag, msg, t)

    fun i(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.INFO, tag, msg, t)

    fun w(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.WARN, tag, msg, t)

    fun e(tag: String, msg: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, msg, t)

    private fun log(level: LogLevel, tag: String, msg: String, t: Throwable?) {
        if (!enabled) return
        delegate?.log(level, tag, msg, t)
    }
}
