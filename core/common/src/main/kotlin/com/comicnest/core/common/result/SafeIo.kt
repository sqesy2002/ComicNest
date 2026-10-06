package com.comicnest.core.common.result

import com.comicnest.core.common.log.AppLog

/**
 * 包裹一次 IO/解码操作：捕获任意 [Throwable] 转为 `Result.failure` 并记日志，绝不向上抛（D11）。
 */
inline fun <T> runCatchingIo(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (t: Throwable) {
        AppLog.e("SafeIo", "IO 操作失败", t)
        Result.failure(t)
    }
}
