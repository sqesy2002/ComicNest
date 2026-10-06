package com.comicnest.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicnest.core.model.ComicSummary
import com.comicnest.core.model.SortMode
import com.comicnest.core.ui.component.ComicCoverCard
import com.comicnest.core.ui.component.EmptyState
import com.comicnest.core.ui.component.ErrorState
import com.comicnest.core.ui.component.FavoritesDialog
import com.comicnest.core.ui.component.LoadingState
import com.comicnest.core.ui.components.DirectoryPickerDialog
import com.comicnest.feature.library.permission.StoragePermission
import com.comicnest.feature.library.viewmodel.LibraryEvent
import com.comicnest.feature.library.viewmodel.LibraryUiState
import com.comicnest.feature.library.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** 分组跳转每组漫画数（DESIGN §10.3，v1 固定 100）。 */
private const val GROUP_SIZE = 100

/**
 * 书架首页（DESIGN §4.1 / §9.2）：无限滚动网格 + 顶栏。
 *
 * 顶部最左 ☰ 打开左侧抽屉（DESIGN §10.1/§10.2/§10.3）：抽屉顶部为搜索框（内存模糊过滤漫画名），
 * 其下按全库每 [GROUP_SIZE] 本列出「第N组 起–止」分组跳转项（与搜索无关），点选滚动到该组并关闭抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToReader: (comicKeyId: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val comicCount by viewModel.comicCount.collectAsStateWithLifecycle()

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
    // 分组跳转待处理目标索引（点分组→关抽屉→（必要时清空搜索后）滚动到该位置）
    var pendingGroupTarget by remember { mutableStateOf<Int?>(null) }
    // 上一次搜索词：仅在用户真正改变搜索词时才回顶，首帧/从阅读器返回时保留滚动位置（诉求②）
    var lastQuery by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is LibraryEvent.NavigateToReader -> onNavigateToReader(e.comicKeyId)
                is LibraryEvent.Message -> snackbarHostState.showSnackbar(e.text)
            }
        }
    }
    LaunchedEffect(query) {
        val previous = lastQuery
        lastQuery = query
        // 首帧（previous==null）不干预，交由 rememberLazyGridState 恢复上次滚动位置；
        // 分组跳转（pendingGroupTarget!=null）交由下方专用 effect 定位，此处不回顶。
        if (previous != null && previous != query && pendingGroupTarget == null) {
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(pendingGroupTarget, state) {
        val target = pendingGroupTarget
        val ready = state as? LibraryUiState.Ready
        if (target != null && ready != null) {
            gridState.scrollToItem(minOf(target, maxOf(ready.comics.size - 1, 0)))
            pendingGroupTarget = null
        }
    }

    val currentGroup by remember {
        derivedStateOf { if (query.isBlank()) gridState.firstVisibleItemIndex / GROUP_SIZE else -1 }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                LibraryDrawerContent(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    totalComics = comicCount,
                    currentGroup = currentGroup,
                    onGroupSelect = { index ->
                        pendingGroupTarget = index * GROUP_SIZE
                        if (query.isNotEmpty()) viewModel.onQueryChange("")
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    // 外层 ComicNestRoot 的 Scaffold 已按 systemBars 消费状态栏高度，
                    // 此处不再自加顶部 inset，避免首页顶栏上方空白翻倍（用户诉求①）。
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "搜索与分组")
                        }
                    },
                    title = { Text("ComicNest") },
                    actions = {
                        SortButton(
                            current = (state as? LibraryUiState.Ready)?.sortMode ?: SortMode.NAME,
                            onSort = viewModel::onSortChange,
                        )
                        IconButton(onClick = viewModel::onRandom) {
                            Icon(Icons.Filled.Casino, contentDescription = "随机阅读")
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
                    !granted -> PermissionGuide(onGrant = { StoragePermission.openSettings(context) })
                    else -> LibraryBody(
                        state = state,
                        gridState = gridState,
                        onComicClick = { onNavigateToReader(it.key.id) },
                        onComicLongPress = { viewModel.onLongPressComic(it.key) },
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
                    onPick = { path ->
                        showAddRoot = false
                        viewModel.onAddRoot(path)
                    },
                    onDismissRequest = { showAddRoot = false },
                )
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

@Composable
private fun LibraryBody(
    state: LibraryUiState,
    gridState: LazyGridState,
    onComicClick: (ComicSummary) -> Unit,
    onComicLongPress: (ComicSummary) -> Unit,
    onAddRoot: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        LibraryUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is LibraryUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is LibraryUiState.Empty -> AddRootGuide(firstRun = state.firstRun, onAddRoot = onAddRoot)
        is LibraryUiState.NoResult -> EmptyState(text = "没有找到匹配「${state.query}」的漫画")
        is LibraryUiState.Ready -> ComicsGrid(
            comics = state.comics,
            gridState = gridState,
            onOpen = onComicClick,
            onLongPress = onComicLongPress,
        )
    }
}

/** 全库为空时的添加入口（DESIGN §10.1：有漫画时不显示，仅无漫画时居中给出）。 */
@Composable
private fun AddRootGuide(firstRun: Boolean, onAddRoot: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (firstRun) "还没有添加任何漫画目录" else "漫画库是空的",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "可能正在扫描中，或已添加目录内没有受支持的漫画",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddRoot) { Text("添加目录") }
    }
}

/** 未授予「所有文件访问权限」时的引导（D2）。 */
@Composable
private fun PermissionGuide(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "需要「所有文件访问权限」才能读取本地漫画目录",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("前往授予") }
    }
}

/**
 * 抽屉内容（DESIGN §10.2/§10.3）：顶部搜索框 + 下方按全库每 [GROUP_SIZE] 本的分组跳转列表。
 * 分组固定按全库总数划分，与搜索无关；结果不足一组时不显示分组区。
 */
@Composable
private fun LibraryDrawerContent(
    query: String,
    onQueryChange: (String) -> Unit,
    totalComics: Int,
    currentGroup: Int,
    onGroupSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索漫画名") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (totalComics > GROUP_SIZE) {
            val groupCount = ceil(totalComics / GROUP_SIZE.toFloat()).toInt()
            Text(
                text = "分组跳转 · 共 $totalComics 部",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            (0 until groupCount).forEach { i ->
                val from = i * GROUP_SIZE + 1
                val to = minOf((i + 1) * GROUP_SIZE, totalComics)
                GroupRow(
                    label = "第${i + 1}组   $from–$to",
                    selected = i == currentGroup,
                    onClick = { onGroupSelect(i) },
                )
            }
        } else {
            Text(
                text = if (totalComics == 0) "漫画库为空" else "漫画不足 $GROUP_SIZE 本，无需分组",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun GroupRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Menu,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ComicsGrid(
    comics: List<ComicSummary>,
    gridState: LazyGridState,
    onOpen: (ComicSummary) -> Unit,
    onLongPress: (ComicSummary) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(comics, key = { it.key.id }) { summary ->
            ComicCoverCard(
                summary = summary,
                onClick = { onOpen(summary) },
                onLongClick = { onLongPress(summary) },
            )
        }
    }
}

private fun sortLabel(mode: SortMode): String = when (mode) {
    SortMode.NAME -> "名称"
    SortMode.IMPORT_TIME -> "导入时间"
}

@Composable
private fun SortButton(current: SortMode, onSort: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "排序")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sortLabel(mode),
                            color = if (mode == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
