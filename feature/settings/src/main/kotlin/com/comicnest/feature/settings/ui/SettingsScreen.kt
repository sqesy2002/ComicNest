package com.comicnest.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicnest.core.model.LibraryRoot
import com.comicnest.core.model.ReaderMode
import com.comicnest.core.model.VideoOrientationMode
import com.comicnest.core.ui.components.DirectoryPickerDialog
import com.comicnest.feature.settings.viewmodel.SettingsEvent
import com.comicnest.feature.settings.viewmodel.SettingsViewModel

/** 设置页（DESIGN §9.8 / §11.6）：库根管理、默认阅读方式、视频播放方式、关于。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 版本号自动对齐：从已安装包的 versionName 读取（不写死，随 Gradle 构建版本变化）
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "未知"
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(e.text)
            }
        }
    }

    var showAddRoot by remember { mutableStateOf(false) }
    var showAddVideoRoot by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item { SectionHeader("漫画库目录") }
            if (state.roots.isEmpty()) {
                item {
                    Text(
                        text = "尚未添加任何漫画目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            state.roots.forEach { root ->
                item {
                    ListItem(
                        headlineContent = { Text(root.displayName) },
                        supportingContent = { Text(root.path) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onRemoveRoot(root) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "移除")
                            }
                        },
                    )
                }
            }
            item {
                TextButton(onClick = { showAddRoot = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("添加漫画目录", modifier = Modifier.padding(start = 4.dp))
                }
            }

            item { Divider() }
            item { SectionHeader("漫画阅读方式") }
            item {
                ListItem(
                    headlineContent = { Text("默认阅读方式") },
                    supportingContent = { Text(modeLabel(state.readerMode)) },
                    modifier = Modifier.clickable { showModeDialog = true },
                )
            }

            item { Divider() }
            item { SectionHeader("视频库目录") }
            if (state.videoRoots.isEmpty()) {
                item {
                    Text(
                        text = "尚未添加任何视频目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            state.videoRoots.forEach { root ->
                item {
                    ListItem(
                        headlineContent = { Text(root.displayName) },
                        supportingContent = { Text(root.path) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.onRemoveVideoRoot(root) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "移除")
                            }
                        },
                    )
                }
            }
            item {
                TextButton(
                    onClick = { showAddVideoRoot = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("添加视频目录", modifier = Modifier.padding(start = 4.dp))
                }
            }

            item { Divider() }
            item { SectionHeader("视频播放方式") }
            item {
                ListItem(
                    headlineContent = { Text("默认视频朝向") },
                    supportingContent = { Text(orientationLabel(state.videoOrientation)) },
                    modifier = Modifier.clickable { showOrientationDialog = true },
                )
            }

            item { Divider() }
            item { SectionHeader("关于") }
            item {
                ListItem(headlineContent = { Text("版本") }, supportingContent = { Text(appVersion) })
            }
            item {
                ListItem(
                    headlineContent = { Text("开源许可") },
                    supportingContent = { Text("基于 Mihon（Apache-2.0）思路移植构建") },
                    modifier = Modifier.clickable { showLicense = true },
                )
            }
        }
    }

    if (showAddRoot) {
        DirectoryPickerDialog(
            onDismissRequest = { showAddRoot = false },
            onPick = {
                showAddRoot = false
                viewModel.onAddRoot(it)
            },
        )
    }

    if (showAddVideoRoot) {
        DirectoryPickerDialog(
            title = "选择视频目录",
            hint = "提示：只识别所选目录直接下一级的视频文件（不递归）；" +
                "Android/data 等系统受限目录无法读取。",
            onDismissRequest = { showAddVideoRoot = false },
            onPick = {
                showAddVideoRoot = false
                viewModel.onAddVideoRoot(it)
            },
        )
    }

    if (showModeDialog) {
        ModeDialog(
            current = state.readerMode,
            onSelect = { viewModel.onReaderModeChange(it) },
            onDismiss = { showModeDialog = false },
        )
    }

    if (showOrientationDialog) {
        OrientationDialog(
            current = state.videoOrientation,
            onSelect = { viewModel.onVideoOrientationChange(it) },
            onDismiss = { showOrientationDialog = false },
        )
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("开源许可") },
            text = {
                Text(
                    "ComicNest 参考并移植了 Mihon（Apache License 2.0）的部分组件思路，" +
                        "并使用 Coil、Room、Hilt、Jetpack Compose 等开源库。感谢开源社区。",
                )
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun modeLabel(mode: ReaderMode): String = when (mode) {
    ReaderMode.WEBTOON -> "下拉式"
    ReaderMode.PAGER -> "左右翻页"
}

@Composable
private fun ModeDialog(
    current: ReaderMode,
    onSelect: (ReaderMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认阅读方式") },
        text = {
            Column {
                ReaderMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode); onDismiss() }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode); onDismiss() })
                        Text(modeLabel(mode))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun orientationLabel(mode: VideoOrientationMode): String = when (mode) {
    VideoOrientationMode.PORTRAIT -> "竖屏"
    VideoOrientationMode.LANDSCAPE -> "横屏"
    VideoOrientationMode.AUTO -> "根据视频比例"
}

private fun orientationHint(mode: VideoOrientationMode): String = when (mode) {
    VideoOrientationMode.PORTRAIT -> "进入播放页保持竖屏"
    VideoOrientationMode.LANDSCAPE -> "进入播放页立即横屏"
    VideoOrientationMode.AUTO -> "横向视频横屏，竖向视频竖屏"
}

@Composable
private fun OrientationDialog(
    current: VideoOrientationMode,
    onSelect: (VideoOrientationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认视频朝向") },
        text = {
            Column {
                VideoOrientationMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode); onDismiss() }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode); onDismiss() })
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(orientationLabel(mode))
                            Text(
                                text = orientationHint(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** ListItem/Row 的轻量点击已统一使用 Modifier.clickable 扩展。 */
