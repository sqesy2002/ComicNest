package com.comicnest.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.comicnest.core.model.ComicSummary

/**
 * 主页网格单元（DESIGN §9.2）：2:3 封面 + 单行省略名称；封面缺失显示占位。
 * 长按用于触发收藏/详情菜单（[onLongClick]）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCoverCard(
    summary: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            ComicImage(
                path = summary.coverPath,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
                contentDescription = summary.key.displayName,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = summary.key.displayName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
