package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.VideoKey
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 按需生成缩略图（DESIGN v2 §3.6）：
 * 同一 key 在生成中复用同一个 Deferred（去重，避免同一视频重复解码）；
 * 已 READY/FAILED 由仓储直接跳过。返回是否真正产出了新缩略图。
 */
class EnsureVideoThumbnailUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Boolean>>()

    suspend operator fun invoke(key: VideoKey): Boolean = coroutineScope {
        mutex.withLock { inFlight[key.id] }?.let { return@coroutineScope it.await() }

        val deferred = async(start = CoroutineStart.LAZY) {
            try {
                videoRepository.generateThumbnail(key).isSuccess
            } finally {
                mutex.withLock { inFlight.remove(key.id) }
            }
        }
        mutex.withLock { inFlight[key.id] = deferred }
        deferred.start()
        deferred.await()
    }
}
