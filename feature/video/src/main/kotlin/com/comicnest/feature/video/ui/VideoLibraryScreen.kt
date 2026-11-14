package com.comicnest.feature.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicnest.core.model.VideoSortMode
import com.comicnest.core.model.VideoSummary
import com.comicnest.core.model.VideoThumbnailState
import com.comicnest.core.ui.component.EmptyState
import com.comicnest.core.ui.component.ErrorState
import com.comicnest.core.ui.component.LoadingState
import com.comicnest.core.ui.components.DirectoryPickerDialog
import com.comicnest.feature.video.permission.StoragePermission
import com.comicnest.feature.video.ui.components.VideoActionSheet
import com.comicnest.feature.video.ui.components.VideoCard
import com.comicnest.feature.video.ui.components.VideoDeleteDialog
import com.comicnest.feature.video.ui.components.VideoDrawer
import com.comicnest.feature.video.ui.components.VideoRenameDialog
import com.comicnest.feature.video.viewmodel.VIDEO_GROUP_SIZE
import com.comicnest.feature.video.viewmodel.VideoEvent
import com.comicnest.feature.video.viewmodel.VideoLibraryUiState
import com.comicnest.feature.video.viewmodel.VideoLibraryViewModel
import kotlinx.coroutines.launch

/**
 * 视频页（DESIGN v2 §9.3 / §11.3）：网格 + 顶栏（排序/随机/刷新）+ 左侧抽屉（搜索 + 每 50 个一组）。
 * 卡片点击播放，长按出动作菜单（播放/重命名/删除）；“继续观看”区块自 v2.2 起不再展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onNavigateToPlayer: (videoKeyId: String, query: String) -> Unit,
    viewModel: VideoLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val actionTarget by viewModel.actionTarget.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val pendingRename by viewModel.pendingRename.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(StoragePermission.isGranted()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = StoragePermission.isGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAddRoot by remember { mutableStateOf(false) }
    var pendingGroupTarget by remember { mutableStateOf<Int?>(null) }
    var lastQuery by remember { mutableStateOf<String?>(null) }

    val ready = state as? VideoLibraryUiState.Ready
    val latestQuery by rememberUpdatedState(query)
    val latestNavigate by rememberUpdatedState(onNavigateToPlayer)

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is VideoEvent.NavigateToPlayer -> latestNavigate(e.videoKeyId, latestQuery)
                is VideoEvent.Message -> snackbarHostState.showSnackbar(e.text)
            }
        }
    }
    LaunchedEffect(query) {
        val previous = lastQuery
        lastQuery = query
        if (previous != null && previous != query && pendingGroupTarget == null) {
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(pendingGroupTarget, state) {
        val target = pendingGroupTarget
        val current = state as? VideoLibraryUiState.Ready
        if (target != null && current != null) {
            gridState.scrollToItem(
                target.coerceAtMost((current.videos.size - 1).coerceAtLeast(0)),
            )
            pendingGroupTarget = null
        }
    }

    // 可见项触发缩略图（懒生成，不预生成）
    LaunchedEffect(gridState, ready?.videos) {
        val videos = ready?.videos ?: return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                indices.forEach { index ->
                    val summary = videos.getOrNull(index) ?: return@forEach
                    if (summary.thumbState == VideoThumbnailState.PENDING) {
                        viewModel.onThumbnailVisible(summary)
                    }
                }
            }
    }

    val currentGroup by remember(query) {
        derivedStateOf {
            if (query.isBlank()) {
                gridState.firstVisibleItemIndex.coerceAtLeast(0) / VIDEO_GROUP_SIZE
            } else {
                -1
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                VideoDrawer(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    totalVideos = totalCount,
                    currentGroup = currentGroup,
                    onGroupSelect = { index ->
                        pendingGroupTarget = index * VIDEO_GROUP_SIZE
                        if (query.isNotEmpty()) viewModel.onQueryChange("")
                        scope.launch { drawerState.close() }
                    },
                    roots = ready?.roots.orEmpty(),
                    onAddRoot = {
                        scope.launch { drawerState.close() }
                        showAddRoot = true
                    },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "搜索与分组")
                        }
                    },
                    title = { Text("视频") },
                    actions = {
                        VideoSortButton(
                            current = ready?.sortMode ?: VideoSortMode.NAME,
                            onSort = viewModel::onSortChange,
                        )
                        IconButton(onClick = viewModel::onRandom) {
                            Icon(Icons.Filled.Casino, contentDescription = "随机播放")
                        }
                        IconButton(onClick = viewModel::onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when {
                    !granted -> PermissionGuide(
                        onGrant = { StoragePermission.openSettings(context) },
                    )

                    else -> VideoBody(
                        state = state,
                        gridState = gridState,
                        onOpen = { viewModel.onOpenVideo(it.key) },
                        onLongPress = viewModel::onLongPressVideo,
                        onAddRoot = { showAddRoot = true },
                        onRetry = viewModel::onRefresh,
                    )
                }
                if (refreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (showAddRoot) {
                DirectoryPickerDialog(
                    title = "选择视频目录",
                    hint = "提示：只识别所选目录直接下一级的视频文件（不递归）；" +
                        "Android/data 等系统受限目录无法读取。",
                    onPick = { path ->
                        showAddRoot = false
                        viewModel.onAddRoot(path)
                    },
                    onDismissRequest = { showAddRoot = false },
                )
            }
        }
    }

    actionTarget?.let { target ->
        VideoActionSheet(
            target = target,
            onPlay = {
                viewModel.onDismissAction()
                viewModel.onOpenVideo(target.key)
            },
            onRename = { viewModel.onRequestRename(target) },
            onDelete = { viewModel.onRequestDelete(target) },
            onDismiss = viewModel::onDismissAction,
        )
    }

    pendingDelete?.let { target ->
        VideoDeleteDialog(
            target = target,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissDelete,
        )
    }

    pendingRename?.let { target ->
        VideoRenameDialog(
            target = target,
            onConfirm = viewModel::onConfirmRename,
            onDismiss = viewModel::onDismissRename,
        )
    }
}

@Composable
private fun VideoBody(
    state: VideoLibraryUiState,
    gridState: LazyGridState,
    onOpen: (VideoSummary) -> Unit,
    onLongPress: (VideoSummary) -> Unit,
    onAddRoot: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        VideoLibraryUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is VideoLibraryUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is VideoLibraryUiState.Empty -> AddVideoRootGuide(
            firstRun = state.firstRun,
            onAddRoot = onAddRoot,
        )

        is VideoLibraryUiState.NoResult -> EmptyState(text = "没有找到匹配「${state.query}」的视频")
        is VideoLibraryUiState.Ready -> VideoGrid(
            videos = state.videos,
            gridState = gridState,
            onOpen = onOpen,
            onLongPress = onLongPress,
        )
    }
}

@Composable
private fun AddVideoRootGuide(firstRun: Boolean, onAddRoot: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (firstRun) "还没有添加任何视频目录" else "视频库是空的",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "只识别所选目录直接下一级的常见视频文件（mp4/mkv/webm 等）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddRoot) { Text("添加视频目录") }
    }
}

@Composable
private fun PermissionGuide(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "需要「所有文件访问权限」才能读取本地视频目录",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("前往授予") }
    }
}

@Composable
private fun VideoGrid(
    videos: List<VideoSummary>,
    gridState: LazyGridState,
    onOpen: (VideoSummary) -> Unit,
    onLongPress: (VideoSummary) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(videos, key = { it.key.id }) { summary ->
            VideoCard(
                summary = summary,
                onClick = { onOpen(summary) },
                onLongClick = { onLongPress(summary) },
            )
        }
    }
}

private fun videoSortLabel(mode: VideoSortMode): String = when (mode) {
    VideoSortMode.NAME -> "名称"
    VideoSortMode.IMPORT_TIME -> "导入时间"
}

@Composable
private fun VideoSortButton(current: VideoSortMode, onSort: (VideoSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VideoSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = videoSortLabel(mode),
                            color = if (mode == current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSort(mode)
                    },
                )
            }
        }
    }
}
