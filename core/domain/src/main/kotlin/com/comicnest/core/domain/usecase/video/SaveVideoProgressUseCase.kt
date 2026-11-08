package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.VideoKey
import javax.inject.Inject

/**
 * 保存观看进度（DESIGN v2 V8）：
 * - `durationMs <= 0` 不写；
 * - `positionMs < 10s` 不写（避免“点开即退出”污染继续观看）；
 * - `positionMs` 夹取到 `[0, durationMs]`。
 * 阈值判断只在此处，UI/DB 均不得绕过。
 */
class SaveVideoProgressUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(key: VideoKey, positionMs: Long, durationMs: Long): Result<Unit> {
        if (durationMs <= 0L) return Result.success(Unit)
        if (positionMs < MIN_POSITION_MS) return Result.success(Unit)
        val clamped = positionMs.coerceIn(0L, durationMs)
        return videoRepository.saveProgress(key, clamped, durationMs)
    }

    companion object {
        const val MIN_POSITION_MS = 10_000L
    }
}
