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
import com.example.iptv.ui.theme.*

/**
 * Cartão estilizado com efeito Glassmorphism avançado:
 * Superfície translúcida escura, borda suave iluminada e efeito de foco D-pad integrado.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = GlassSurfaceDark,
    isFocusable: Boolean = true,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused && isFocusable) 1.035f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "glassCardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused && isFocusable) AccentCoral else GlassBorderSubtle,
        animationSpec = tween(durationMillis = 180),
        label = "glassCardBorder"
    )

    val shadowElevation = if (isFocused && isFocusable) elevation + 8.dp else elevation

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(shadowElevation, shape, spotColor = if (isFocused) AccentCoral else Color.Black)
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = if (isFocused) 0.85f else 0.70f),
                        backgroundColor.copy(alpha = if (isFocused) 0.95f else 0.82f)
                    )
                )
            )
            .border(
                border = BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = borderColor
                ),
                shape = shape
            )
            .then(
                if (isFocusable) {
                    Modifier
                        .focusable(interactionSource = interactionSource)
                        .then(
                            if (onClick != null) {
                                Modifier.clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = onClick
                                )
                            } else Modifier
                        )
                } else {
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                }
            ),
        content = content
    )
}
