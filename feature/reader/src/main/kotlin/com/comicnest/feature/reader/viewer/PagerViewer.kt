package com.comicnest.feature.reader.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.ui.component.ComicImage

/**
 * 左右翻页（Pager，DESIGN §3.7/§9.4）：`HorizontalPager` 单页 fit 屏幕。
 * [onPageChanged] 由当前页变化驱动，供 ViewModel 节流保存进度（D6）。
 * [currentPage] 为外部真实页码（含底部滑块拖动），与当前页不一致时回滚到该页。
 */
@Composable
fun PagerViewer(
    pages: List<ComicPageRef>,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { onPageChanged(it) }
    }
    // 外部（滑块）改变了目标页且与当前页不一致时，直接定位过去。
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) {
            pagerState.scrollToPage(currentPage.coerceIn(0, pages.lastIndex))
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        ComicImage(
            path = pages[page].absPath,
            isAnimated = pages[page].isAnimated,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            contentDescription = "第 ${page + 1} 页",
        )
    }
}
