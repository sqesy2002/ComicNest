package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.model.LibraryRoot
import javax.inject.Inject

/**
 * 新增库根：先做基础入参校验，再委托仓储做"存在/是目录/可读"的落地校验（DESIGN §3.3）。
 */
class AddLibraryRootUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(path: String): Result<LibraryRoot> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("库根路径不能为空"))
        }
        return libraryRepository.addRoot(path)
    }
}
