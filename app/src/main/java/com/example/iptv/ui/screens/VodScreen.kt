package com.example.iptv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.data.model.VodItem
import com.example.iptv.ui.components.CategoryRow
import com.example.iptv.ui.components.EmptyState
import com.example.iptv.ui.components.ErrorState
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.LoadingState
import com.example.iptv.ui.components.PosterCard
import com.example.iptv.ui.components.SearchField
import com.example.iptv.ui.components.SelectAllChip
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.viewmodel.MainTvViewModel

/**
 * Movies catalogue: categories + live search on the left, poster grid on the right.
 * Clicking a poster opens the fullscreen ExoPlayer with `/movie/user/pass/id.ext`.
 */
@Composable
fun VodScreen(viewModel: MainTvViewModel) {
    val categories by viewModel.vodCategories.collectAsState()
    val items by viewModel.vodItems.collectAsState()
    val allItems by viewModel.vodAll.collectAsState()
    val loading by viewModel.vodLoading.collectAsState()
    val error by viewModel.vodError.collectAsState()
    val categoryFilter by viewModel.vodCategoryFilter.collectAsState()
    val query by viewModel.vodQuery.collectAsState()

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
                        onValueChange = { viewModel.vodQuery.value = it },
                        placeholder = "Buscar filme…"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SelectAllChip(
                        label = "Todos os filmes (${allItems.size})",
                        isSelected = categoryFilter == null,
                        onClick = { viewModel.selectVodCategory(null) }
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
                                    onClick = { viewModel.selectVodCategory(category.id) }
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
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FILMES",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${items.size} títulos disponíveis",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = { viewModel.loadVod(force = true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar filmes",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    when {
                        loading && items.isEmpty() -> LoadingState(
                            message = "Carregando filmes…",
                            modifier = Modifier.weight(1f)
                        )

                        error != null && items.isEmpty() -> ErrorState(
                            message = error.orEmpty(),
                            onRetry = { viewModel.loadVod(force = true) },
                            modifier = Modifier.weight(1f)
                        )

                        items.isEmpty() -> EmptyState(
                            title = "Nenhum filme encontrado",
                            message = "Ajuste a busca ou escolha outra categoria. Se o catálogo estiver vazio, o painel pode não oferecer VOD.",
                            icon = Icons.Default.Movie,
                            modifier = Modifier.weight(1f)
                        )

                        else -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            MovieGrid(
                                items = items,
                                onPlay = viewModel::playMovie
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieGrid(
    items: List<VodItem>,
    onPlay: (VodItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 2.dp,
            end = 12.dp,
            top = 2.dp,
            bottom = 20.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(items, key = { it.id }) { item ->
            PosterCard(
                imageUrl = item.icon,
                title = item.name,
                subtitle = item.extension.uppercase(),
                rating = item.rating ?: item.rating5?.let { String.format("%.1f", it) },
                onClick = { onPlay(item) },
                modifier = Modifier.fillMaxWidth(),
                width = null
            )
        }
    }
}
