package com.comicnest.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BlueOnContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BlueOnContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
)

/**
 * ComicNest Material3 主题（DESIGN §3.6 / §9.2）。
 * 默认跟随系统深浅色；Android 12+ 支持动态取色（Material You）。
 */
@Composable
fun ComicNestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ComicNestTypography,
        content = content,
    )
}
