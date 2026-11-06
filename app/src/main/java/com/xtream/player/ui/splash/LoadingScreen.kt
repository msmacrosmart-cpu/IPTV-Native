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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
        // The supplied widescreen artwork belongs exclusively to the loading screen.
        // Fit preserves its proportions on both TV and wide landscape phones; the
        // surrounding black matches the image and avoids stretching the logo.
        Image(painterResource(R.drawable.splash_art), contentDescription = "IPTV Native",
            contentScale = ContentScale.Fit, filterQuality = FilterQuality.High,
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(.85f)
                .fillMaxHeight(.75f).testTag("loading-art"))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 10.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingWheel(Modifier.size(if (compact) 30.dp else 44.dp))
            Spacer(Modifier.height(if (compact) 5.dp else 10.dp))
            Text(message, color = Color.White, fontSize = if (compact) 13.sp else 17.sp,
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
