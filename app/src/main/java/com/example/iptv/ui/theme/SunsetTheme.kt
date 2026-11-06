package com.example.iptv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Paleta extraída das telas de referência (fundo grafite + riscas neon "entardecer"). */
object SunsetColors {
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF1A1A1D)
    val GlassTop = Color(0x26FFFFFF)
    val GlassBottom = Color(0x0AFFFFFF)
    val Coral = Color(0xFFEF5350)
    val Pink = Color(0xFFFF3D5A)
    val Orange = Color(0xFFFF8A00)
    val Yellow = Color(0xFFFFD23F)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFFA8A8AD)
    val Success = Color(0xFF4CAF50)
    val Danger = Color(0xFFFF5252)

    val Sunset: Brush get() = Brush.linearGradient(listOf(Pink, Orange, Yellow))
}

@Composable
fun SunsetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SunsetColors.Coral,
            background = SunsetColors.Background,
            surface = SunsetColors.Surface,
            onPrimary = Color.White,
            onBackground = SunsetColors.TextPrimary,
            onSurface = SunsetColors.TextPrimary,
        ),
        typography = Typography(),
        content = content,
    )
}
