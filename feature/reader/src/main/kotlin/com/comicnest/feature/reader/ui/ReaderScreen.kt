package com.comicnest.feature.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.ui.component.ErrorState
import com.comicnest.core.ui.component.FavoritesDialog
import com.comicnest.core.ui.component.LoadingState
import com.comicnest.feature.reader.viewmodel.ReaderUiState
import com.comicnest.feature.reader.viewmodel.ReaderViewModel
import com.comicnest.feature.reader.viewer.PagerViewer
import com.comicnest.feature.reader.viewer.WebtoonViewer
import kotlin.math.roundToInt

/**
 * 阅读器全屏页（DESIGN §9.4）：轻点内容区显隐上下两栏；按模式渲染 Webtoon/Pager 视图。
 * - 上栏：返回、漫画名（点名称展全名、点别处收起）、排版切换、收藏；大体保持不变。
 * - 下栏：横向滑块快速翻页（左端 1、右端总页数、居中当前页），同时驱动上下与左右两种模式。
 * - 切换模式时以当前页作为新视图起点，保持页码不跳（§9.4）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.readerMode.collectAsStateWithLifecycle()
    val page by viewModel.currentPage.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    var barsVisible by rememberSaveable { mutableStateOf(true) }
    var expandedTitle by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 两栏隐藏（点击内容区）时，展开的全名一并收起，恢复原样。
    LaunchedEffect(barsVisible) { if (!barsVisible) expandedTitle = false }

    val ready = state as? ReaderUiState.Ready

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (barsVisible) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    title = {
                        Text(
                            text = if (ready != null) "${ready.title}  ${page + 1}/${ready.pages.size}" else "阅读",
                            maxLines = if (expandedTitle) Int.MAX_VALUE else 1,
                            overflow = if (expandedTitle) androidx.compose.ui.text.style.TextOverflow.Clip
                            else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expandedTitle = !expandedTitle },
                        )
                    },
                    actions = {
                        IconButton(onClick = viewModel::onToggleMode) {
                            val icon = if (mode == ReaderMode.WEBTOON) Icons.Filled.ViewDay else Icons.Filled.GridView
                            Icon(icon, contentDescription = if (mode == ReaderMode.WEBTOON) "切换翻页模式" else "切换下拉模式")
                        }
                        IconButton(onClick = viewModel::onFavoriteClick) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "收藏",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (barsVisible && ready != null) {
                ReaderBottomBar(
                    currentPage = page,
                    total = ready.pages.size,
                    onSeek = viewModel::onPageChanged,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { barsVisible = !barsVisible })
                },
        ) {
            when (val s = state) {
                ReaderUiState.Loading -> LoadingState()
                is ReaderUiState.Error -> ErrorState(s.message)
                is ReaderUiState.Ready -> when (mode) {
                    ReaderMode.WEBTOON -> WebtoonViewer(
                        pages = s.pages,
                        currentPage = page,
                        onPageChanged = viewModel::onPageChanged,
                    )
                    ReaderMode.PAGER -> PagerViewer(
                        pages = s.pages,
                        currentPage = page,
                        onPageChanged = viewModel::onPageChanged,
                    )
                }
            }
        }
    }

    favorites?.let { fav ->
        FavoritesDialog(
            favorites = fav,
            onToggle = viewModel::onToggleFavorite,
            onDismiss = viewModel::onDismissFavorites,
        )
    }
}

/**
 * 底部翻页滑块栏（DESIGN §9.4 新增）：居中展示当前页码，滑轨两端标注「1」与总页数；
 * 拖动即时 `onSeek` 定位（与两种阅读模式共用同一 [currentPage]，故自适应上下/左右翻页）。
 */
@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    total: Int,
    onSeek: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = "${currentPage + 1} / $total",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "1",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(28.dp),
                )
                Slider(
                    value = if (total <= 1) 0f else currentPage.toFloat() / (total - 1),
                    onValueChange = { frac ->
                        if (total > 1) onSeek((frac * (total - 1)).roundToInt().coerceIn(0, total - 1))
                    },
                    enabled = total > 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
    }
}
