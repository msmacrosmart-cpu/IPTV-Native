package com.example.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.GlassBorderSubtle
import com.example.iptv.ui.theme.GlassSurfaceDark

/**
 * Glassmorphic surface with optional D-pad focus animation.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = GlassSurfaceDark,
    isFocusable: Boolean = false,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused && isFocusable) 1.02f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "glassCardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused && isFocusable) AccentCoral else GlassBorderSubtle,
        animationSpec = tween(durationMillis = 160),
        label = "glassCardBorder"
    )

    val focusModifier = when {
        isFocusable && onClick != null -> Modifier
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)

        isFocusable -> Modifier.focusable(interactionSource = interactionSource)
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isFocused && isFocusable) elevation + 6.dp else elevation,
                shape = shape,
                spotColor = if (isFocused) AccentCoral else Color.Black
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.92f),
                        backgroundColor.copy(alpha = 0.78f)
                    )
                )
            )
            .border(
                border = BorderStroke(
                    width = if (isFocused && isFocusable) 2.dp else 1.dp,
                    color = borderColor
                ),
                shape = shape
            )
            .then(focusModifier),
        content = content
    )
}
