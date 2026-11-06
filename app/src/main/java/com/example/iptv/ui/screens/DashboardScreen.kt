package com.example.iptv.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.SeriesItem
import com.example.iptv.data.model.VodItem
import com.example.iptv.ui.components.AppLogo
import com.example.iptv.ui.components.ChannelLogo
import com.example.iptv.ui.components.EmptyState
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.LoadingState
import com.example.iptv.ui.components.PosterCard
import com.example.iptv.ui.components.PrimaryButton
import com.example.iptv.ui.components.SectionHeader
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.LiveRed
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.ui.theme.TextSecondary
import com.example.iptv.ui.theme.SuccessGreen
import com.example.iptv.util.FormatUtils
import com.example.iptv.viewmodel.MainTvViewModel
import com.example.iptv.viewmodel.TvNavSection

/**
 * Home screen: real account summary + hero of the first available title + horizontal shelves
 * filled with live channels, movies and series coming straight from the panel.
 */
@Composable
fun DashboardScreen(
    viewModel: MainTvViewModel,
    onNavigate: (TvNavSection) -> Unit
) {
    val account by viewModel.account.collectAsState()
    val channels by viewModel.liveAll.collectAsState()
    val movies by viewModel.vodAll.collectAsState()
    val series by viewModel.seriesAll.collectAsState()
    val liveLoading by viewModel.liveLoading.collectAsState()
    val vodLoading by viewModel.vodLoading.collectAsState()
    val seriesLoading by viewModel.seriesLoading.collectAsState()

    val heroMovie = remember(movies) { movies.firstOrNull { !it.icon.isNullOrBlank() } ?: movies.firstOrNull() }
    val heroSeries = remember(series) { series.firstOrNull { !(it.backdrop ?: it.cover).isNullOrBlank() } }
    val anyLoading = (liveLoading || vodLoading || seriesLoading) &&
        channels.isEmpty() && movies.isEmpty() && series.isEmpty()

    TopographicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp)
        ) {
            // ------------------------------------------------------------- header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLogo(markSize = 42)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        label = account?.statusLabel ?: "Conectando",
                        active = account?.isActive == true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = account?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    SmallAction(
                        icon = Icons.Default.Refresh,
                        description = "Atualizar catálogo",
                        onClick = viewModel::refreshAll
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (anyLoading) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    LoadingState(
                        message = "Sincronizando catálogo do painel…",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                return@Column
            }

            if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    EmptyState(
                        title = "Catálogo vazio",
                        message = "O painel não retornou canais, filmes ou séries. Verifique se a conta está ativa e use o botão de atualizar acima.",
                        icon = Icons.Default.LiveTv,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                return@Column
            }

            // --------------------------------------------------------------- hero
            val heroMovieItem = heroMovie
            val heroSeriesItem = if (heroMovieItem == null) heroSeries else null
            val heroTitle = heroMovieItem?.name ?: heroSeriesItem?.name
            val heroSubtitle = when {
                heroMovieItem != null -> listOfNotNull(
                    heroMovieItem.rating?.let { "★ $it" },
                    "FILME"
                ).joinToString("  ·  ")

                heroSeriesItem != null -> listOfNotNull(
                    heroSeriesItem.rating?.let { "★ $it" },
                    heroSeriesItem.releaseDate?.take(4),
                    "SÉRIE"
                ).joinToString("  ·  ")

                else -> ""
            }

            if (heroTitle != null) {
                HeroCard(
                    title = heroTitle,
                    subtitle = heroSubtitle,
                    imageUrl = heroMovieItem?.icon ?: heroSeriesItem?.let { it.backdrop ?: it.cover },
                    plot = heroMovieItem?.plot ?: heroSeriesItem?.plot,
                    onPlay = {
                        val movie = heroMovieItem
                        val serie = heroSeriesItem
                        if (movie != null) {
                            viewModel.playMovie(movie)
                        } else if (serie != null) {
                            viewModel.openSeriesDetail(serie)
                            onNavigate(TvNavSection.SERIES)
                        }
                    },
                    onDetails = {
                        onNavigate(if (heroMovieItem != null) TvNavSection.MOVIES else TvNavSection.SERIES)
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))
            }

            // ------------------------------------------------------------- shelves
            if (channels.isNotEmpty()) {
                SectionHeader(
                    title = "Ao vivo agora",
                    trailing = { SeeAllLink(onClick = { onNavigate(TvNavSection.LIVE_TV) }) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(channels.take(20), key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { viewModel.playChannelFullscreen(channel) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (movies.isNotEmpty()) {
                SectionHeader(
                    title = "Filmes",
                    trailing = { SeeAllLink(onClick = { onNavigate(TvNavSection.MOVIES) }) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(movies.take(20), key = { it.id }) { movie ->
                        PosterCard(
                            imageUrl = movie.icon,
                            title = movie.name,
                            subtitle = movie.extension.uppercase(),
                            rating = movie.rating,
                            onClick = { viewModel.playMovie(movie) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (series.isNotEmpty()) {
                SectionHeader(
                    title = "Séries",
                    trailing = { SeeAllLink(onClick = { onNavigate(TvNavSection.SERIES) }) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(series.take(20), key = { it.id }) { item ->
                        PosterCard(
                            imageUrl = item.cover ?: item.backdrop,
                            title = item.name,
                            subtitle = item.genre,
                            rating = item.rating,
                            onClick = { viewModel.openSeriesDetail(item) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ----------------------------------------------------- account footer
            account?.let { info ->
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoPill(
                        icon = Icons.Default.Movie,
                        label = "${FormatUtils.formatCount(movies.size)} filmes"
                    )
                    InfoPill(
                        icon = Icons.Default.VideoLibrary,
                        label = "${FormatUtils.formatCount(series.size)} séries"
                    )
                    InfoPill(
                        icon = Icons.Default.LiveTv,
                        label = "${FormatUtils.formatCount(channels.size)} canais"
                    )
                    InfoPill(
                        icon = Icons.Default.Refresh,
                        label = "Expira em ${FormatUtils.formatDate(info.expiresAtMillis)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    plot: String?,
    onPlay: () -> Unit,
    onDetails: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF211A1A), Color(0xFF0C0D12))
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xF20A0B10),
                                Color(0xE60A0B10),
                                Color(0x660A0B10),
                                Color(0x0A0A0B10)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 30.dp)
                    .width(520.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!plot.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = plot,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        label = "Assistir",
                        icon = Icons.Default.PlayArrow,
                        onClick = onPlay,
                        modifier = Modifier.width(170.dp)
                    )
                    GhostButton(
                        label = "Ver catálogo",
                        onClick = onDetails,
                        modifier = Modifier.width(170.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: LiveChannel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .width(230.dp)
            .height(70.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color(0x33FF6B5B) else Color(0x22191C26))
            .border(
                1.5.dp,
                if (isFocused) AccentCoral else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(url = channel.icon, name = channel.name, size = 42)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(LiveRed))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "AO VIVO", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusDot(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) SuccessGreen else LiveRed)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = TextMuted, fontSize = 10.5.sp)
    }
}

@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Text(
        text = "Ver tudo  →",
        color = if (isFocused) Color.White else AccentCoral,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) AccentCoral else Color.Transparent)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isFocused) AccentCoral else Color(0x1AFFFFFF))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isFocused) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(24.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
