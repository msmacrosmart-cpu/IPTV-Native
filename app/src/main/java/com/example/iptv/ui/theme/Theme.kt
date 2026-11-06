package com.example.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCoral,
    onPrimary = Color.White,
    secondary = AccentOrange,
    onSecondary = Color.White,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceDark,
    onSurfaceVariant = TextSecondary
)

@Composable
fun IPTVNativeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
