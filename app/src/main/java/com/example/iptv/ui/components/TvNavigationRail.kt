package com.example.iptv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.iptv.R
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.viewmodel.TvNavSection

/**
 * Left navigation rail (Android TV friendly): logo on top, main sections in the middle,
 * settings pinned to the bottom.
 */
@Composable
fun TvNavigationRail(
    currentSection: TvNavSection,
    onSectionSelected: (TvNavSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(Color(0xF2090A0F))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_mark),
                contentDescription = "IPTV Native",
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .size(44.dp)
            )

            TvRailIconButton(
                icon = Icons.Default.Home,
                contentDescription = "Início",
                isSelected = currentSection == TvNavSection.HOME,
                onClick = { onSectionSelected(TvNavSection.HOME) }
            )
            TvRailIconButton(
                icon = Icons.Default.LiveTv,
                contentDescription = "TV ao Vivo",
                isSelected = currentSection == TvNavSection.LIVE_TV,
                onClick = { onSectionSelected(TvNavSection.LIVE_TV) }
            )
            TvRailIconButton(
                icon = Icons.Default.Movie,
                contentDescription = "Filmes",
                isSelected = currentSection == TvNavSection.MOVIES,
                onClick = { onSectionSelected(TvNavSection.MOVIES) }
            )
            TvRailIconButton(
                icon = Icons.Default.VideoLibrary,
                contentDescription = "Séries",
                isSelected = currentSection == TvNavSection.SERIES,
                onClick = { onSectionSelected(TvNavSection.SERIES) }
            )
        }

        TvRailIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Configurações",
            isSelected = currentSection == TvNavSection.SETTINGS,
            onClick = { onSectionSelected(TvNavSection.SETTINGS) }
        )
    }
}

@Composable
private fun TvRailIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        isSelected -> Color(0x33FF6B5B)
        isFocused -> Color(0x33FFFFFF)
        else -> Color.Transparent
    }
    val iconColor = when {
        isSelected -> AccentCoral
        isFocused -> Color.White
        else -> Color.White.copy(alpha = 0.5f)
    }
    val borderColor = when {
        isSelected -> AccentCoral
        isFocused -> Color.White.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(15.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}
