package com.comicnest.core.domain.usecase.video

import com.comicnest.core.common.util.VideoFileNames
import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.VideoKey
import javax.inject.Inject

/**
 * 重命名视频（DESIGN v2 追加）：只改主名、原扩展名固定保留。
 * 名称非法时直接返回失败，不触碰文件；名称未变化时视为成功。
 */
class RenameVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(key: VideoKey, baseName: String): Result<VideoKey> {
        val normalized = VideoFileNames.validateBaseName(baseName)
            .getOrElse { return Result.failure(it) }
        val newFileName = VideoFileNames.fileNameFor(normalized, key.fileName)
        if (newFileName == key.fileName) return Result.success(key)
        return videoRepository.renameVideo(key, newFileName)
    }
}
