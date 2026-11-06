package com.sunsettv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunsettv.app.ui.theme.SunsetColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Painel "glass" escuro com borda translúcida (visual do Login). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(SunsetColors.Surface.copy(alpha = 0.72f))
            .background(Brush.verticalGradient(listOf(SunsetColors.GlassTop, SunsetColors.GlassBottom)))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x1AFFFFFF))), shape),
        content = content,
    )
}

/**
 * Superfície focável para D-pad + toque. Foco = borda neon entardecer + glow + leve zoom.
 * OK/Enter/DPAD_CENTER disparam [onClick]. [onFocused] é chamado quando ganha foco.
 */
@Composable
fun SunsetFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    selected: Boolean = false,
    focusScale: Float = 1.03f,
    idleColor: Color = Color(0x14FFFFFF),
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(150), label = "focusScale")
    val border: Brush = when {
        focused -> SunsetColors.Sunset
        selected -> SolidColor(SunsetColors.Coral.copy(alpha = 0.7f))
        else -> SolidColor(Color(0x1AFFFFFF))
    }
    val bg = when {
        focused -> Color(0x33FFFFFF)
        selected -> Color(0x1FFFFFFF)
        else -> idleColor
    }
    Box(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 14.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = SunsetColors.Orange,
                spotColor = SunsetColors.Pink,
            )
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) { content(focused) }
}

@Composable
fun SunsetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val tint = if (danger) SunsetColors.Danger else SunsetColors.Coral
    SunsetFocusable(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(50),
        idleColor = tint.copy(alpha = 0.22f),
        focusScale = 1.04f,
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = SunsetColors.TextPrimary, modifier = Modifier.size(20.dp))
            Text(text, color = SunsetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column {
        Text(
            title,
            color = SunsetColors.TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(subtitle, color = SunsetColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
fun SunsetProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    brush: Brush = SunsetColors.Sunset,
) {
    val shape = RoundedCornerShape(50)
    Box(modifier.height(4.dp).clip(shape).background(Color(0x33FFFFFF))) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(shape)
                .background(brush),
        )
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(5.dp)).background(SunsetColors.Coral).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SunsetColors.Coral)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, color = SunsetColors.TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
        SunsetButton("Tentar novamente", onRetry)
    }
}

/** Relógio que atualiza a cada [intervalMs] (para barras de progresso do EPG). */
@Composable
fun rememberNowMs(intervalMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun formatClock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
fun formatDateTime(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))
