package com.comicnest.feature.collection.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicnest.core.model.Collection
import com.comicnest.core.ui.component.EmptyState
import com.comicnest.core.ui.component.LoadingState
import com.comicnest.feature.collection.ui.components.CollectionNameDialog
import com.comicnest.feature.collection.viewmodel.CollectionListEvent
import com.comicnest.feature.collection.viewmodel.CollectionListUiState
import com.comicnest.feature.collection.viewmodel.CollectionListViewModel

/** 收藏夹列表页（DESIGN §9.5）：卡片网格 + 新建 FAB + 卡片菜单（重命名/删除）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionListScreen(
    onNavigateToDetail: (Long) -> Unit,
    viewModel: CollectionListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is CollectionListEvent.Message -> snackbarHostState.showSnackbar(e.text)
            }
        }
    }

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Collection?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("我的收藏") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("新建") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { showCreate = true },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val s = state) {
                CollectionListUiState.Loading -> LoadingState()
                CollectionListUiState.Empty -> EmptyState(
                    text = "还没有收藏夹，点击右下角新建",
                    actionLabel = "新建收藏夹",
                    onAction = { showCreate = true },
                )
                is CollectionListUiState.Ready -> CollectionGrid(
                    collections = s.collections,
                    onClick = { onNavigateToDetail(it.id) },
                    onRename = { renameTarget = it },
                    onDelete = { viewModel.onDelete(it.id, it.name) },
                )
            }
        }
    }

    if (showCreate) {
        CollectionNameDialog(
            title = "新建收藏夹",
            confirmLabel = "创建",
            onDismiss = { showCreate = false },
            onConfirm = {
                showCreate = false
                viewModel.onCreate(it)
            },
        )
    }

    renameTarget?.let { target ->
        CollectionNameDialog(
            title = "重命名收藏夹",
            confirmLabel = "保存",
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                renameTarget = null
                viewModel.onRename(target.id, it)
            },
        )
    }
}

@Composable
private fun CollectionGrid(
    collections: List<Collection>,
    onClick: (Collection) -> Unit,
    onRename: (Collection) -> Unit,
    onDelete: (Collection) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(collections, key = { it.id }) { collection ->
            CollectionCard(
                collection = collection,
                onClick = { onClick(collection) },
                onRename = { onRename(collection) },
                onDelete = { onDelete(collection) },
            )
        }
    }
}

@Composable
private fun CollectionCard(
    collection: Collection,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = collection.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
