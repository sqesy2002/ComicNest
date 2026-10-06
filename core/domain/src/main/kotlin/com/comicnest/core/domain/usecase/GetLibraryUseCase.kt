package com.comicnest.core.domain.usecase

import com.comicnest.core.domain.model.LibraryUiData
import com.comicnest.core.domain.repository.LibraryRepository
import com.comicnest.core.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 合并"当前排序 → 漫画列表 + 库根"为单一 [LibraryUiData] 流（DESIGN §3.3）。
 * 排序变更时以 flatMapLatest 重新订阅漫画流。
 */
class GetLibraryUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<LibraryUiData> =
        preferencesRepository.observeSortMode().flatMapLatest { sort ->
            combine(libraryRepository.observeComics(sort), libraryRepository.observeRoots()) { comics, roots ->
                LibraryUiData(comics = comics, sortMode = sort, roots = roots)
            }
        }
}
