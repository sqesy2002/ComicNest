package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.model.OpenComicData
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.ProgressRepository
import com.comicnest.core.model.ComicKey
import javax.inject.Inject

/**
 * 打开漫画：取自然序页 + 续读起始页。进度命中则夹取到 `[0, total-1]`（§8.2），否则从第一页。
 */
class OpenComicUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(key: ComicKey): Result<OpenComicData> {
        val pages = libraryRepository.getPages(key).getOrElse { return Result.failure(it) }
        if (pages.isEmpty()) {
            return Result.failure(NoSuchElementException("该漫画没有可读页面"))
        }
        val progress = progressRepository.getProgress(key)
        val startIndex = progress?.pageIndex?.coerceIn(0, pages.size - 1) ?: 0
        return Result.success(OpenComicData(pages = pages, startIndex = startIndex))
    }
}
