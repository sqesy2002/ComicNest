package com.comicnest.core.domain.usecase.video

import com.comicnest.core.domain.repository.VideoRepository
import com.comicnest.core.model.VideoKey
import com.comicnest.core.model.VideoSortMode
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/** 在全部视频中均匀随机取一个（V3）；空库返回 failure。`[random]` 可注入以便测试确定性。 */
class RandomVideoUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(random: Random = Random.Default): Result<VideoKey> {
        val videos = videoRepository.observeVideos(VideoSortMode.NAME).first()
        if (videos.isEmpty()) {
            return Result.failure(NoSuchElementException("视频库为空，请先添加视频目录"))
        }
        return Result.success(videos[random.nextInt(videos.size)].key)
    }
}
