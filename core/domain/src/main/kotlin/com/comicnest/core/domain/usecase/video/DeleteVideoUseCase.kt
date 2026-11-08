package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.VideoKey
import javax.inject.Inject

/**
 * 删除视频（DESIGN v2 §3.7）：委托仓储执行五步流水线。
 * 返回后 UI 只依赖 DB Flow 自然刷新，不做手动剔除。
 */
class DeleteVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(key: VideoKey): Result<Unit> = videoRepository.deleteVideo(key)
}
