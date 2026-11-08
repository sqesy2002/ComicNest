package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.VideoRoot
import javax.inject.Inject

/**
 * 新增视频库根（DESIGN v2 §3.3/V11）：先做入参校验，再委托仓储做存在/是目录/可读校验。
 * 与漫画库根允许重叠（两域独立存表）。
 */
class AddVideoRootUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(path: String): Result<VideoRoot> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("视频库目录不能为空"))
        }
        return videoRepository.addRoot(path)
    }
}
