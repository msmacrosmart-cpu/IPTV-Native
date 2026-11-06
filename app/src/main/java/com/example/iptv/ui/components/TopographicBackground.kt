package com.example.iptv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Dark "damask topographic" background drawn procedurally with Canvas — no bitmap, so it stays
 * crisp on phones, tablets and 4K TVs, and it never shows a stretched screenshot.
 */
@Composable
fun TopographicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0F16),
                        Color(0xFF090A0F),
                        Color(0xFF06070A)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.minDimension

            // Warm coral glow top-right.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33FF6B5B), Color(0x11FF6B5B), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.05f),
                    radius = unit * 0.95f
                ),
                radius = unit * 0.95f,
                center = Offset(size.width * 0.82f, size.height * 0.05f)
            )

            // Cool glow bottom-left, keeps the composition balanced.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x221E3A5F), Color(0x0D1E3A5F), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.95f),
                    radius = unit * 0.85f
                ),
                radius = unit * 0.85f,
                center = Offset(size.width * 0.1f, size.height * 0.95f)
            )

            // Topographic contour rings (bronze), two organic clusters.
            drawContourCluster(
                center = Offset(size.width * 0.16f, size.height * 0.92f),
                baseRadius = unit * 0.18f,
                step = unit * 0.085f,
                rings = 12,
                color = Color(0x1A8A5540)
            )
            drawContourCluster(
                center = Offset(size.width * 0.92f, size.height * 0.12f),
                baseRadius = unit * 0.12f,
                step = unit * 0.075f,
                rings = 10,
                color = Color(0x148A5540)
            )
        }

        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawContourCluster(
    center: Offset,
    baseRadius: Float,
    step: Float,
    rings: Int,
    color: Color
) {
    for (index in 0 until rings) {
        val radius = baseRadius + index * step
        val width = radius * 2.6f
        val height = radius * 1.7f
        drawOval(
            color = color,
            topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
