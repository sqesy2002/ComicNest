package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.repository.ProgressRepository
import com.comicnest.core.model.ComicKey
import javax.inject.Inject

/** 保存进度并触发仓储内 LRU=5 淘汰（D6）。 */
class SaveProgressUseCase @Inject constructor(
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(key: ComicKey, pageIndex: Int, total: Int): Result<Unit> =
        progressRepository.saveProgress(key, pageIndex, total)
}
