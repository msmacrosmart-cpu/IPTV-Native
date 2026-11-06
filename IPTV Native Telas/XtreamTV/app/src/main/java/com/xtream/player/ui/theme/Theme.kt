package com.xtream.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object XColors {
    val Bg = Color(0xFF0E0E10)
    val Panel = Color(0xFF151517)
    val Sidebar = Color(0xFF111113)
    val Accent = Color(0xFFF0564A)
    val Gold = Color(0xFFFFC043)
    val Blue = Color(0xFF4F6BFF)
    val BlueSoft = Color(0xFF7C92FF)
    val Text = Color(0xFFF2F2F3)
    val TextDim = Color(0xFFA7A7AD)
    val Line = Color(0x24FFFFFF)
    val Field = Color(0x14FFFFFF)
    val Contour = Color(0xFF9B4332)
    val Live = Color(0xFFF0564A)
    val Ok = Color(0xFF43C77A)
}

@Composable
fun XtreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = XColors.Accent, background = XColors.Bg, surface = XColors.Panel,
            onSurface = XColors.Text, onBackground = XColors.Text, onPrimary = Color.White,
        ),
        content = content,
    )
}
