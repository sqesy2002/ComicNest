package com.comicnest.core.domain.model

import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.SortMode

/** [GetLibraryUseCase] 输出：漫画列表 + 当前排序 + 库根。 */
data class LibraryUiData(
    val comics: List<ComicSummary>,
    val sortMode: SortMode,
    val roots: List<LibraryRoot>,
)

/** [OpenComicUseCase] 输出：自然序页列表 + 续读起始页（LRU 命中则续读，否则 0）。 */
data class OpenComicData(
    val pages: List<ComicPageRef>,
    val startIndex: Int,
)
