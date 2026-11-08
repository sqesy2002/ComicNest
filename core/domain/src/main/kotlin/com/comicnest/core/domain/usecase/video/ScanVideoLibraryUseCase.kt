package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import javax.inject.Inject

/** 触发全量重扫（DESIGN v2 §3.3）；失败时保留旧数据（上层只提示）。 */
class ScanVideoLibraryUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(): Result<Int> = videoRepository.refresh()
}
