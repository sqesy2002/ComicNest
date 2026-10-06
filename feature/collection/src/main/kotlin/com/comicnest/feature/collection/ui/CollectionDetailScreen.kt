package com.comicnest.feature.collection.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.ui.component.ComicCoverCard
import com.comicnest.core.ui.component.EmptyState
import com.comicnest.core.ui.component.ErrorState
import com.comicnest.core.ui.component.LoadingState
import com.comicnest.feature.collection.ui.components.CollectionNameDialog
import com.comicnest.feature.collection.viewmodel.CollectionDetailEvent
import com.comicnest.feature.collection.viewmodel.CollectionDetailUiState
import com.comicnest.feature.collection.viewmodel.CollectionDetailViewModel

/** 收藏夹详情页（DESIGN §9.5）：该夹内漫画网格；长按卡片移出；顶栏菜单重命名/删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onNavigateToReader: (comicKeyId: String) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is CollectionDetailEvent.Message -> snackbarHostState.showSnackbar(e.text)
                CollectionDetailEvent.NavigateBack -> onBack()
            }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val currentName = (state as? CollectionDetailUiState.Ready)?.name

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(currentName ?: "收藏夹", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                onClick = { menuOpen = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("删除收藏夹") },
                                onClick = { menuOpen = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = state) {
                CollectionDetailUiState.Loading -> LoadingState()
                CollectionDetailUiState.NotFound -> ErrorState("收藏夹不存在或已被删除")
                CollectionDetailUiState.Empty -> EmptyState("这个收藏夹还没有漫画，去书架长按漫画加入吧")
                is CollectionDetailUiState.Ready -> ComicsGrid(
                    comics = s.comics,
                    paddingValues = innerPadding,
                    onOpen = { onNavigateToReader(it.key.id) },
                    onRemove = { viewModel.onRemoveComic(it.key) },
                )
            }
        }
    }

    if (showRename && currentName != null) {
        CollectionNameDialog(
            title = "重命名收藏夹",
            confirmLabel = "保存",
            initial = currentName,
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                viewModel.onRename(it)
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除收藏夹") },
            text = { Text("确定删除「$currentName」？其中的收藏关系将一并移除（不影响漫画本身）。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.onDelete() }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ComicsGrid(
    comics: List<ComicSummary>,
    paddingValues: PaddingValues,
    onOpen: (ComicSummary) -> Unit,
    onRemove: (ComicSummary) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(comics, key = { it.key.id }) { summary ->
            ComicCoverCard(
                summary = summary,
                onClick = { onOpen(summary) },
                onLongClick = { onRemove(summary) },
            )
        }
    }
}
