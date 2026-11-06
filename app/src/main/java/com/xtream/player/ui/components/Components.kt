package com.xtream.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xtream.player.data.VodItem
import com.xtream.player.ui.theme.DmSans
import com.xtream.player.ui.theme.XColors
import java.util.Locale

val FocusBrush: Brush = SolidColor(XColors.Focus)

/** Focus is visible at a distance on TV; keep modifier nodes stable during animation. */
fun Modifier.focusGlow(
    shape: Shape = RoundedCornerShape(10.dp),
    width: Dp = 2.dp,
    zoom: Float = 1.015f,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "focus")
    this.onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = 1f + (zoom - 1f) * t; scaleY = 1f + (zoom - 1f) * t
            shadowElevation = 8.dp.toPx() * t
            this.shape = shape; clip = false
            ambientShadowColor = XColors.Focus; spotShadowColor = XColors.Focus
        }
        .border(width, if (focused) FocusBrush else SolidColor(Color.Transparent), shape)
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp), content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(XColors.Panel).border(1.dp, Color(0x0DFFFFFF), shape), content = content)
}

/** A platform text input: the device's own Android/TV IME is used, never a custom keyboard. */
@Composable
fun PillField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = XColors.Text, fontSize = 16.sp, fontFamily = DmSans),
        cursorBrush = SolidColor(XColors.Accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.heightIn(min = 52.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape).background(XColors.Field)
                .border(if (focused) 2.dp else 1.dp, if (focused) XColors.Focus else XColors.Line, shape)
                .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = XColors.TextDim, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable fun TopoBackground(modifier: Modifier = Modifier.fillMaxSize()) { Box(modifier.background(XColors.Shell)) }
@Composable fun AmbientBackground(modifier: Modifier = Modifier.fillMaxSize()) { Box(modifier.background(XColors.Bg)) }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = XColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryChip(text: String, selected: Boolean, dropdown: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.focusGlow(shape).clip(shape)
        .background(if (selected) XColors.Selection else XColors.Panel)
        .border(1.dp, if (selected) XColors.Accent.copy(alpha = .55f) else Color.Transparent, shape)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = if (selected) XColors.BlueSoft else XColors.Text, fontSize = 14.sp, maxLines = 1)
        if (dropdown) Icon(Icons.Filled.ArrowDropDown, null, tint = XColors.TextDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PosterArt(item: VodItem, modifier: Modifier = Modifier) {
    var failed by remember(item.poster) { mutableStateOf(false) }
    var loaded by remember(item.poster) { mutableStateOf(false) }
    val imageTag = if (loaded) Modifier.testTag("loaded-poster-${item.favoriteKey}") else Modifier
    Box(modifier.then(imageTag).background(XColors.Panel), contentAlignment = Alignment.Center) {
        if (item.poster.isNullOrBlank() || failed || !loaded) {
            Icon(if (item.isSeries) Icons.Filled.Tv else Icons.Filled.Movie, null,
                tint = XColors.Accent.copy(alpha = .5f), modifier = Modifier.size(42.dp))
        }
        if (!item.poster.isNullOrBlank() && !failed) {
            AsyncImage(item.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true }, onError = { loaded = false; failed = true })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(item: VodItem, width: Dp = 148.dp, onLongClick: () -> Unit = {},
               onClick: () -> Unit, isFavorite: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).testTag("poster-${item.favoriteKey}")
            .focusGlow(shape).clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
            PosterArt(item, Modifier.fillMaxSize())
            if (item.rating > 0) {
                Row(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0D0F12)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = XColors.Ok, modifier = Modifier.size(13.dp))
                    Text(" ${String.format(Locale.US, "%.1f", item.rating)}", color = XColors.Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (isFavorite) Icon(Icons.Filled.Favorite, "Favorito", tint = XColors.Live,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(18.dp))
            if (item.progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color(0x664F5666))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress.coerceIn(0f, 1f)).background(XColors.Accent))
            }
        }
        Text(item.title, color = XColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, top = 7.dp))
        if (item.progress > 0f) Text("Continuar assistindo", color = XColors.Body, fontSize = 11.sp,
            maxLines = 1, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
    }
}

@Composable
fun LandscapeCard(item: VodItem, width: Dp = 148.dp, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    PosterCard(item, width, onLongClick, onClick)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlowButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, ringAlways: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val bg = if (ringAlways) Color(0xFFF2F4F8) else Color(0x1FFFFFFF)
    val fg = if (ringAlways) Color(0xFF0A0E14) else XColors.Text
    Row(modifier.focusGlow(shape).clip(shape).background(bg)
        .border(1.dp, if (ringAlways) Color.Transparent else XColors.Line, shape)
        .combinedClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.padding(end = 8.dp).size(19.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
