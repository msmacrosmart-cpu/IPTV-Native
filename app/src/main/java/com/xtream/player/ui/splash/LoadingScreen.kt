package com.xtream.player.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Shown while the saved account and its on-disk snapshot are actually being restored. */
@Composable
fun LoadingScreen(message: String) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("loading-screen")
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center) {
        val compact = maxHeight < 380.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.brand_square), contentDescription = "IPTV Native",
                modifier = Modifier.size(if (compact) 96.dp else 144.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
            LoadingWheel(Modifier.size(if (compact) 40.dp else 52.dp))
            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
            Text(message, color = Color.White, fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.testTag("loading-message"))
        }
    }
}

/** Twelve rounded spokes, matching the supplied loading reference, with a smooth rotation. */
@Composable
private fun LoadingWheel(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading-wheel")
    val angle by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart), label = "rotation")
    Canvas(modifier.semantics { contentDescription = "Carregando" }) {
        val outer = size.minDimension * .47f
        val inner = size.minDimension * .30f
        val stroke = size.minDimension * .10f
        repeat(12) { index ->
            val radians = (angle + index * 30f - 90f) * PI / 180.0
            val x = cos(radians).toFloat()
            val y = sin(radians).toFloat()
            drawLine(Color.White.copy(alpha = .16f + .84f * (12 - index) / 12f),
                Offset(center.x + x * inner, center.y + y * inner),
                Offset(center.x + x * outer, center.y + y * outer), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
