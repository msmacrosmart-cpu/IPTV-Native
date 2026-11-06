package com.example.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.R
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.RatingGold
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** App logo mark + wordmark used on the login screen and splash. */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    markSize: Int = 56,
    showWordmark: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo_mark),
            contentDescription = "IPTV Native",
            modifier = Modifier.size(markSize.dp)
        )
        if (showWordmark) {
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "IPTV NATIVE",
                    color = TextPrimary,
                    fontSize = (markSize * 0.4f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "player xtream",
                    color = AccentCoral,
                    fontSize = (markSize * 0.21f).sp,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33191C26)),
        placeholder = {
            Text(
                text = placeholder,
                fontSize = 13.sp,
                color = TextMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) AccentCoral else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpar busca",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCoral,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentCoral,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        interactionSource = interactionSource
    )
}

/** "Selecionar Todos" chip. */
@Composable
fun SelectAllChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isSelected -> Brush.horizontalGradient(listOf(Color(0x4DFF6B5B), Color(0x1AFF6B5B)))
        isFocused -> Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF)))
        else -> Brush.horizontalGradient(listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (isFocused) 1.6.dp else 1.dp,
                color = when {
                    isSelected -> AccentCoral
                    isFocused -> Color.White.copy(alpha = 0.55f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "★  $label",
            color = if (isSelected || isFocused) Color.White else TextSecondary,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Category row used by every section list. */
@Composable
fun CategoryRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0x33FF6B5B)
            isFocused -> Color(0x22FFFFFF)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "categoryBackground"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                width = if (isSelected || isFocused) 1.dp else 0.dp,
                color = when {
                    isSelected -> AccentCoral
                    isFocused -> Color.White.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = if (isSelected || isFocused) Color.White else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

@Composable
fun LoadingState(message: String = "Carregando…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(34.dp),
            color = AccentCoral,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = message, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inbox
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(46.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(360.dp)
        )
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Não foi possível carregar"
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = AccentCoral,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(420.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text(text = "Tentar novamente", color = AccentCoral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Focusable pill button with the brand gradient. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(140),
        label = "primaryButtonScale"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (enabled) {
                        listOf(Color(0xFFFF7A5C), Color(0xFFFF5247))
                    } else {
                        listOf(Color(0xFF3A3D48), Color(0xFF2C2F38))
                    }
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Poster card for movies and series (2:3 artwork). */
@Composable
fun PosterCard(
    imageUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: String? = null,
    width: androidx.compose.ui.unit.Dp? = 148.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "posterScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) AccentCoral else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(150),
        label = "posterBorder"
    )

    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x22191C26))
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF11131B)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials(title),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!rating.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "★", color = RatingGold, fontSize = 9.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = rating, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
    }
}

/** Channel logo box with initials fallback. */
@Composable
fun ChannelLogo(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 46
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF12141C))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } else {
            Text(
                text = initials(name),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = (size * 0.28f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Floating message used for repository alerts. */
@Composable
fun AlertBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(message) {
        delay(4_500)
        onDismiss()
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF21A1D27))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(AccentCoral)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f, fill = false)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fechar",
                tint = TextMuted,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun initials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
