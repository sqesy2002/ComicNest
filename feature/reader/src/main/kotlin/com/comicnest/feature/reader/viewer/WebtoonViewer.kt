package com.comicnest.feature.reader.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.comicnest.core.model.ComicPageRef
import com.comicnest.core.ui.component.ComicImage

/**
 * 下拉式连续长图（Webtoon，默认，DESIGN §3.7/§9.4）：`LazyColumn` 无缝拼接，图片按屏宽等比。
 * [onPageChanged] 由首个可见项变化驱动，供 ViewModel 节流保存进度（D6）。
 * [currentPage] 为外部真实页码（含底部滑块拖动），非用户滚动导致其变化时回滚到该页。
 */
@Composable
fun WebtoonViewer(
    pages: List<ComicPageRef>,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, pages.lastIndex))
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { onPageChanged(it) }
    }
    // 外部（滑块）改变了目标页且与当前可见页不一致时，直接定位过去（快速翻页）。
    LaunchedEffect(currentPage) {
        if (listState.firstVisibleItemIndex != currentPage) {
            listState.scrollToItem(currentPage.coerceIn(0, pages.lastIndex))
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(pages, key = { _, page -> page.index }) { _, page ->
            ComicImage(
                path = page.absPath,
                isAnimated = page.isAnimated,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = "第 ${page.index + 1} 页",
            )
        }
    }
}
