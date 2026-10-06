package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.repository.CollectionRepository
import javax.inject.Inject

/**
 * 以当前有效漫画键集合校验收藏 entry，删除失效指向并返回清理数（D7）。
 * 有效性判定由 `:core:data` 的 CollectionRepository 实现内联完成（读取 comic_cache）。
 */
class CleanupInvalidCollectionsUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
) {
    suspend operator fun invoke(): Result<Int> =
        collectionRepository.cleanupInvalid()
}
