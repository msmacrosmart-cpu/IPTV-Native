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
    val Sidebar = Color(0xFF111318)
    val Bg = Color(0xFF161A22)
    val Panel = Color(0xFF1E2330)
    val PanelHeader = Color(0xFF232840)
    val CardHover = Color(0xFF252B3A)
    val Accent = Color(0xFF78ADFF)
    val Blue = Accent
    val BlueSoft = Color(0xFFA9CAFF)
    val Focus = Color(0xFF8CBAFF)
    val Text = Color(0xFFD8DCE8)
    val TextDim = Color(0xFFA1AABD)
    val Body = Color(0xFF8B93A8)
    val Line = Color(0x24FFFFFF)
    val Field = Color(0x0DFFFFFF)
    val Gold = Color(0xFFFBBF24)
    val Live = Color(0xFFFF8F8F)
    val Ok = Color(0xFF4ADE80)
    val Contour = Accent
    val Selection = Color(0x2E78ADFF)
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
