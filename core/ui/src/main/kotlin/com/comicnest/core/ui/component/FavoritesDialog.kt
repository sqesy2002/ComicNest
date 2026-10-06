package com.comicnest.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comicnest.core.model.Collection
import com.comicnest.core.model.ComicKey

/**
 * 收藏多选对话框状态（纯 UI 载体，DESIGN §10.4）：书架长按与阅读器 ♡ 共用。
 *
 * @param key 目标漫画内部键（用于展示名与加入/移出）。
 * @param collections 全部收藏夹。
 * @param selected 该漫画当前已属的收藏夹 id 集合（多对多，D7）。
 */
data class FavoritesUiState(
    val key: ComicKey,
    val collections: List<Collection>,
    val selected: Set<Long>,
)

/**
 * 加入收藏夹多选对话框（DESIGN §3.7 / §9.7 / §10.4，D7 多对多）：列出全部收藏夹，勾选即加入/取消即移出。
 * 尚无收藏夹时提示前往「收藏」页新建。书架与阅读器共用此组件，数据同源、指向式不复制文件。
 */
@Composable
fun FavoritesDialog(
    favorites: FavoritesUiState,
    onToggle: (collectionId: Long, add: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入收藏") },
        text = {
            Column {
                Text(
                    text = favorites.key.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (favorites.collections.isEmpty()) {
                    Text("还没有收藏夹，请先到「收藏」页新建。", style = MaterialTheme.typography.bodySmall)
                } else {
                    favorites.collections.forEach { collection ->
                        val checked = collection.id in favorites.selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(collection.id, !checked) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { add -> onToggle(collection.id, add) })
                            Text(collection.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
