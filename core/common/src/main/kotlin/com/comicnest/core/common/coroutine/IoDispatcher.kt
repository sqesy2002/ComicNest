package com.comicnest.core.common.coroutine

import javax.inject.Qualifier

/**
 * IO 调度器限定符：统一注入 `Dispatchers.IO`，便于在测试中替换（DESIGN §3.2）。
 * 具体 `@Provides` 由 Android 层 DI（`:core:data`）实现，此处仅提供注解，保持 common 纯净。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
