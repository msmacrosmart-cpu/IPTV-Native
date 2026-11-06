package com.example.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.data.model.Episode
import com.example.iptv.data.model.SeasonEpisodes
import com.example.iptv.data.model.SeriesItem
import com.example.iptv.ui.components.CategoryRow
import com.example.iptv.ui.components.EmptyState
import com.example.iptv.ui.components.ErrorState
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.LoadingState
import com.example.iptv.ui.components.PosterCard
import com.example.iptv.ui.components.SearchField
import com.example.iptv.ui.components.SelectAllChip
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.ui.theme.TextSecondary
import com.example.iptv.viewmodel.MainTvViewModel

/**
 * Series catalogue with a built-in detail view: selecting a cover loads
 * `get_series_info`, listing seasons and episodes ready to stream through the player.
 */
@Composable
fun SeriesScreen(viewModel: MainTvViewModel) {
    val categories by viewModel.seriesCategories.collectAsState()
    val items by viewModel.seriesItems.collectAsState()
    val allItems by viewModel.seriesAll.collectAsState()
    val loading by viewModel.seriesLoading.collectAsState()
    val error by viewModel.seriesError.collectAsState()
    val categoryFilter by viewModel.seriesCategoryFilter.collectAsState()
    val query by viewModel.seriesQuery.collectAsState()
    val selectedSeries by viewModel.selectedSeries.collectAsState()
    val seasons by viewModel.seriesSeasons.collectAsState()
    val detailLoading by viewModel.seriesDetailLoading.collectAsState()
    val detailError by viewModel.seriesDetailError.collectAsState()

    BackHandler(enabled = selectedSeries != null) { viewModel.closeSeriesDetail() }

    TopographicBackground {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(
                modifier = Modifier.width(250.dp).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { viewModel.seriesQuery.value = it },
                        placeholder = "Buscar série…"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SelectAllChip(
                        label = "Todas as séries (${allItems.size})",
                        isSelected = categoryFilter == null,
                        onClick = { viewModel.selectSeriesCategory(null) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "CATEGORIAS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                    )

                    if (loading && categories.isEmpty()) {
                        LoadingState(message = "Carregando…", modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(categories, key = { it.id }) { category ->
                                CategoryRow(
                                    label = category.name,
                                    isSelected = category.id == categoryFilter,
                                    onClick = { viewModel.selectSeriesCategory(category.id) }
                                )
                            }
                        }
                    }
                }
            }

            GlassCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp)
            ) {
                val current = selectedSeries
                if (current != null) {
                    SeriesDetail(
                        series = current,
                        seasons = seasons,
                        loading = detailLoading,
                        error = detailError,
                        onBack = viewModel::closeSeriesDetail,
                        onRetry = { viewModel.openSeriesDetail(current) },
                        onPlayEpisode = { season, episode ->
                            viewModel.playEpisode(current, season, episode)
                        }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SÉRIES",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${items.size} séries disponíveis",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.loadSeries(force = true) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Atualizar séries",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        when {
                            loading && items.isEmpty() -> LoadingState(
                                message = "Carregando séries…",
                                modifier = Modifier.weight(1f)
                            )

                            error != null && items.isEmpty() -> ErrorState(
                                message = error.orEmpty(),
                                onRetry = { viewModel.loadSeries(force = true) },
                                modifier = Modifier.weight(1f)
                            )

                            items.isEmpty() -> EmptyState(
                                title = "Nenhuma série encontrada",
                                message = "Ajuste a busca ou escolha outra categoria.",
                                icon = Icons.Default.VideoLibrary,
                                modifier = Modifier.weight(1f)
                            )

                            else -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 148.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(start = 2.dp, end = 12.dp, top = 2.dp, bottom = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(items, key = { it.id }) { item ->
                                    PosterCard(
                                        imageUrl = item.cover ?: item.backdrop,
                                        title = item.name,
                                        subtitle = item.releaseDate?.take(4) ?: item.genre,
                                        rating = item.rating ?: item.rating5?.let { String.format("%.1f", it) },
                                        onClick = { viewModel.openSeriesDetail(item) },
                                        modifier = Modifier.fillMaxWidth(),
                                        width = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesDetail(
    series: SeriesItem,
    seasons: List<SeasonEpisodes>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlayEpisode: (SeasonEpisodes, Episode) -> Unit
) {
    var selectedSeasonIndex by remember(series.id) { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = series.name,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(156.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF11131B))
            ) {
                if (!(series.cover ?: series.backdrop).isNullOrBlank()) {
                    AsyncImage(
                        model = series.cover ?: series.backdrop,
                        contentDescription = series.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                val meta = listOfNotNull(
                    series.rating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
                    series.releaseDate?.take(4),
                    series.genre
                ).filter { it.isNotBlank() }

                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString("  ·  "),
                        color = AccentCoral,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = series.plot?.takeIf { it.isNotBlank() } ?: "Sem sinopse disponível para esta série.",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )

                series.cast?.takeIf { it.isNotBlank() }?.let { cast ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Elenco: $cast",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            loading -> LoadingState(message = "Carregando episódios…", modifier = Modifier.weight(1f))
            error != null -> ErrorState(
                message = error,
                onRetry = onRetry,
                title = "Episódios indisponíveis",
                modifier = Modifier.weight(1f)
            )
            seasons.isEmpty() -> EmptyState(
                title = "Sem episódios",
                message = "Esta série não possui episódios publicados no painel.",
                icon = Icons.Default.VideoLibrary,
                modifier = Modifier.weight(1f)
            )
            else -> {
                val safeIndex = selectedSeasonIndex.coerceIn(0, seasons.size - 1)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons.size) { index ->
                        SeasonChip(
                            label = "Temporada ${seasons[index].season}",
                            isSelected = index == safeIndex,
                            onClick = { selectedSeasonIndex = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val episodes = seasons[safeIndex].episodes
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            onClick = { onPlayEpisode(seasons[safeIndex], episode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AccentCoral else Color(0x22FFFFFF))
            .border(
                1.dp,
                if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (isFocused) Color(0x33FF6B5B) else Color(0x1A161922))
            .border(
                1.dp,
                if (isFocused) AccentCoral else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(11.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) AccentCoral else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "E${episode.number} · ${episode.title}",
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!episode.plot.isNullOrBlank()) {
                Text(
                    text = episode.plot.orEmpty(),
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        episode.durationLabel?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, color = TextMuted, fontSize = 10.sp)
        }
    }
}
