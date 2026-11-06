package com.xtream.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.iptv.R

/** Dark graphite tokens from IPTVnator's web dark palette. */
object XColors {
    val Shell = Color(0xFF0D0F12)
    // Neutral translucent surfaces let the supplied background show through, including
    // selected rows and the login panel. Focus still has a high-contrast white outline.
    val Sidebar = Color(0xE60D0F12)
    val Bg = Color(0xFF111214)
    val Panel = Color(0xC90D0F12)
    val PanelHeader = Color(0xE018191B)
    val CardHover = Color(0xD02A2B2D)
    val Accent = Color(0xFFE2E4E8)
    val Blue = Accent
    val BlueSoft = Color(0xFFF0F1F3)
    val Focus = Color(0xFF86B8FF)
    val Text = Color(0xFFE3E4E7)
    val TextDim = Color(0xFFB4B6BB)
    val Body = Color(0xFF999CA3)
    val Line = Color(0x24FFFFFF)
    val Field = Color(0x0DFFFFFF)
    val Gold = Color(0xFFFBBF24)
    val Live = Color(0xFFFF8F8F)
    val Ok = Color(0xFF4ADE80)
    val Contour = Accent
    val Selection = Color(0xC9262729)
}

val DmSans = FontFamily(Font(R.font.dm_sans))

@Composable
fun XtreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = XColors.Accent, background = XColors.Bg, surface = XColors.Panel,
            onSurface = XColors.Text, onBackground = XColors.Text, onPrimary = XColors.Shell,
            outline = XColors.TextDim,
        ),
        typography = Typography(bodyLarge = TextStyle(fontFamily = DmSans, fontSize = 16.sp)),
        content = content,
    )
}
