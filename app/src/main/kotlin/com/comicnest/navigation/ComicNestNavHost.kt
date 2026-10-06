package com.comicnest.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.graphics.vector.ImageVector
import com.comicnest.feature.collection.ui.CollectionDetailScreen
import com.comicnest.feature.collection.ui.CollectionListScreen
import com.comicnest.feature.library.ui.LibraryScreen
import com.comicnest.feature.reader.ui.ReaderScreen
import com.comicnest.feature.settings.ui.SettingsScreen

/**
 * 全局导航骨架（DESIGN §3.7 / §9.1）：单 Activity + 底部导航 + NavHost。
 * 阅读器 [Routes.READER] 与收藏详情 [Routes.COLLECTION_DETAIL] 为全屏页，隐藏底部导航栏。
 */
@Composable
fun ComicNestRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = if (showBottomBar) Modifier.padding(innerPadding) else Modifier,
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onNavigateToReader = { comicKeyId ->
                        navController.navigate("${Routes.READER}/${Uri.encode(comicKeyId)}")
                    },
                )
            }
            composable(
                route = "${Routes.READER}/{comicKeyId}",
                arguments = listOf(navArgument("comicKeyId") { type = NavType.StringType }),
            ) {
                // ReaderViewModel 从 SavedStateHandle 读取 comicKeyId；返回弹出阅读页
                ReaderScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.COLLECTIONS) {
                CollectionListScreen(
                    onNavigateToDetail = { id -> navController.navigate("${Routes.COLLECTION}/$id") },
                )
            }
            composable(
                route = "${Routes.COLLECTION}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                CollectionDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToReader = { comicKeyId ->
                        navController.navigate("${Routes.READER}/${Uri.encode(comicKeyId)}")
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

/** 底部导航三入口（书架 / 收藏 / 设置），DESIGN §9.1。 */
enum class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Library(Routes.LIBRARY, "书架", Icons.Filled.MenuBook),
    Collections(Routes.COLLECTIONS, "收藏", Icons.Filled.Favorite),
    Settings(Routes.SETTINGS, "设置", Icons.Filled.Settings),
}

/** 展示底部导航栏的路由集合；其余（reader / collection 详情）为全屏页。 */
private val BOTTOM_BAR_ROUTES = BottomDestination.entries.map { it.route }.toSet()

/** 路由常量（app 定义，feature 间通过路由字符串解耦，DESIGN §2）。 */
object Routes {
    const val LIBRARY = "library"
    const val READER = "reader"
    const val COLLECTIONS = "collections"
    const val COLLECTION = "collection"
    const val SETTINGS = "settings"
}
