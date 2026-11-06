package com.example.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.GlassBorderSubtle
import com.example.iptv.viewmodel.TvNavSection

@Composable
fun TvNavigationRail(
    currentSection: TvNavSection,
    onSectionSelected: (TvNavSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(Color(0xFF090A0D))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TvRailIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Buscar",
                isSelected = currentSection == TvNavSection.SEARCH,
                onClick = { onSectionSelected(TvNavSection.SEARCH) }
            )

            TvRailIconButton(
                icon = Icons.Default.Home,
                contentDescription = "Início",
                isSelected = currentSection == TvNavSection.HOME,
                onClick = { onSectionSelected(TvNavSection.HOME) }
            )

            TvRailIconButton(
                icon = Icons.Default.Movie,
                contentDescription = "Filmes",
                isSelected = currentSection == TvNavSection.MOVIES,
                onClick = { onSectionSelected(TvNavSection.MOVIES) }
            )

            TvRailIconButton(
                icon = Icons.Default.LiveTv,
                contentDescription = "TV ao Vivo",
                isSelected = currentSection == TvNavSection.LIVE_TV,
                onClick = { onSectionSelected(TvNavSection.LIVE_TV) }
            )

            TvRailIconButton(
                icon = Icons.Default.Star,
                contentDescription = "Favoritos",
                isSelected = currentSection == TvNavSection.FAVORITES,
                onClick = { onSectionSelected(TvNavSection.FAVORITES) }
            )
        }

        // Configurações na parte inferior
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
        isSelected -> Color(0x334E75FF)
        isFocused -> Color(0x33FF574D)
        else -> Color.Transparent
    }

    val iconColor = when {
        isSelected -> Color(0xFF6B94FF)
        isFocused -> AccentCoral
        else -> Color.White.copy(alpha = 0.5f)
    }

    val borderColor = when {
        isSelected -> Color(0x664E75FF)
        isFocused -> AccentCoral
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
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
