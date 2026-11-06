package com.xtream.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xtream.player.data.VodItem
import com.xtream.player.ui.theme.XColors
import kotlin.math.cos
import kotlin.math.sin

val FocusBrush: Brush = Brush.linearGradient(listOf(XColors.Accent, XColors.Gold))

/** Anel degradê vermelho→dourado + brilho + leve zoom quando o item recebe foco (D-pad ou toque). */
fun Modifier.focusGlow(
    shape: Shape = RoundedCornerShape(14.dp),
    width: Dp = 2.5.dp,
    zoom: Float = 1.04f,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(140), label = "focus")
    // Keep the modifier nodes stable while focus animates. Conditionally inserting a border or
    // shadow before a clickable can recreate its focus target and immediately drop TV focus.
    this
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = 1f + (zoom - 1f) * t; scaleY = 1f + (zoom - 1f) * t
            shadowElevation = 14.dp.toPx() * t
            this.shape = shape; clip = false
            ambientShadowColor = XColors.Accent; spotShadowColor = XColors.Gold
        }
        .border(width, if (focused) FocusBrush else SolidColor(Color.Transparent), shape)
}

/** Painel "glassmorphism": vidro escuro, brilho diagonal e borda clara. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(Color(0xE6141416))
            .background(Brush.linearGradient(listOf(Color(0x3DFFFFFF), Color(0x08FFFFFF), Color(0x1FFFFFFF))))
            .border(1.5.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x40FFFFFF))), shape),
        content = content,
    )
}

@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = XColors.Text, fontSize = 18.sp),
        cursorBrush = SolidColor(XColors.Accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.height(58.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().clip(shape).background(XColors.Field)
                    .border(if (focused) 2.dp else 1.dp, if (focused) FocusBrush else SolidColor(XColors.Line), shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, color = XColors.TextDim, fontSize = 18.sp)
                inner()
            }
        },
    )
}

/** Fundo topográfico do login (curvas de nível terracota sobre preto). */
@Composable
fun TopoBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier.background(XColors.Bg)) {
        val w = size.width; val h = size.height
        val lines = 44
        for (k in 0 until lines) {
            val base = h * (k / (lines - 1f)) * 1.35f - h * 0.17f
            val path = Path()
            var x = 0f
            var first = true
            while (x <= w + 8f) {
                val u = x / w
                val y = base + 0.085f * h * sin(u * 5.2f + k * 0.19f) +
                    0.05f * h * sin(u * 11f - k * 0.11f + 1.3f) + 0.03f * h * cos(u * 3f + k * 0.31f)
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 8f
            }
            drawPath(path, XColors.Contour.copy(alpha = 0.55f), style = Stroke(1.4f))
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, XColors.Bg.copy(alpha = 0.55f)), center = center, radius = w * 0.75f))
    }
}

/** Fundo dos painéis: brilho quente à direita + faixa diagonal vermelho/dourado. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(XColors.Bg)
        drawRect(Brush.radialGradient(listOf(Color(0x66C2612B), Color.Transparent), center = Offset(w * 0.78f, h * 0.22f), radius = w * 0.55f))
        val a = Offset(w * 0.43f, 0f); val b = Offset(w * 0.64f, h)
        drawLine(Brush.linearGradient(listOf(XColors.Accent.copy(alpha = 0.3f), XColors.Gold.copy(alpha = 0.3f)), a, b), a, b, strokeWidth = 26f)
        drawLine(Brush.linearGradient(listOf(XColors.Accent, XColors.Gold), a, b), a, b, strokeWidth = 5f)
        drawRect(Brush.horizontalGradient(listOf(XColors.Bg, XColors.Bg.copy(alpha = 0.55f), Color.Transparent), startX = 0f, endX = w * 0.6f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, XColors.Bg.copy(alpha = 0.85f)), startY = h * 0.5f, endY = h))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = XColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryChip(text: String, selected: Boolean, dropdown: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.focusGlow(shape, zoom = 1.03f).clip(shape)
            .background(if (selected) Color(0x33FFFFFF) else Color(0x1FFFFFFF))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = XColors.Text, fontSize = 18.sp, maxLines = 1)
        if (dropdown) Icon(Icons.Filled.KeyboardArrowDown, null, tint = XColors.Text, modifier = Modifier.padding(start = 6.dp).size(22.dp))
    }
}

@Composable
fun PosterArt(item: VodItem, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(Color(item.c1), Color(item.c2))))) {
        if (item.poster != null) {
            AsyncImage(item.poster, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                item.title.uppercase(), color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 4,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(item: VodItem, width: Dp = 142.dp, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(width)) {
        PosterArt(
            item,
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).testTag("poster-${item.favoriteKey}").focusGlow(shape).clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
        Text(item.title, color = XColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandscapeCard(item: VodItem, width: Dp = 150.dp, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.width(width)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).focusGlow(shape).clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            PosterArt(item, Modifier.fillMaxSize())
            if (item.progress > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp).height(3.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress).background(XColors.Accent))
                }
            }
        }
        Text(item.title, color = XColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlowButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    ringAlways: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.focusGlow(shape).clip(shape).background(Color(0xCC0E0E10))
            .then(if (ringAlways) Modifier.border(2.dp, FocusBrush, shape) else Modifier.border(1.dp, XColors.Line, shape))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = XColors.Text, modifier = Modifier.padding(end = 10.dp).size(22.dp))
        Text(text, color = XColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
