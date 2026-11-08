package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import javax.inject.Inject

/** 移除视频库根（连带该根的视频缓存、观看记录与缩略图，DESIGN v2 §3.3）。 */
class RemoveVideoRootUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(rootId: String): Result<Unit> = videoRepository.removeRoot(rootId)
}
