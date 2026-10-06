package com.comicnest.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale as ComposeContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * 单张图片加载（Coil）。[isAnimated] 为 gif 标记（实际播放由全局 ImageLoader 的 GifDecoder 负责，D8）；
 * 加载失败/缺失显示断图占位（D11），绝不崩。
 */
@Composable
fun ComicImage(
    path: String?,
    modifier: Modifier = Modifier,
    isAnimated: Boolean = false,
    contentScale: ComposeContentScale = ComposeContentScale.Fit,
    contentDescription: String? = null,
) {
    if (path.isNullOrBlank()) {
        ImagePlaceholder(modifier, contentScale)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(File(path))
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        loading = { LoadingPlaceholder() },
        error = { ImagePlaceholder(Modifier.fillMaxSize(), contentScale) },
        modifier = modifier,
    )
}

@Composable
private fun ImagePlaceholder(modifier: Modifier, contentScale: ComposeContentScale) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = "无法显示",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
