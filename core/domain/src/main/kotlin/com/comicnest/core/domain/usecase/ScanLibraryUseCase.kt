package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * 触发全量重扫；扫描成功后清理失效收藏指向（DESIGN §3.3）。
 */
class ScanLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val cleanupInvalidCollections: CleanupInvalidCollectionsUseCase,
) {
    suspend operator fun invoke(): Result<Int> {
        val result = libraryRepository.refresh()
        result.onSuccess { cleanupInvalidCollections() }
        return result
    }
}
