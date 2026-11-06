package com.example.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.data.model.SeriesCategory
import com.example.iptv.data.model.SeriesStream
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.*
import com.example.iptv.viewmodel.MainTvViewModel

/**
 * Tela de Séries em 2 Colunas:
 * - Coluna 1: Categorias de Séries + Selecionar Todos + Busca por Título
 * - Coluna 2: Grid de Pôsteres com Metadados (nota ★, temporadas) e suporte rigoroso ao foco D-pad
 */
@Composable
fun SeriesScreen(
    viewModel: MainTvViewModel,
    onPlaySeries: (url: String, title: String) -> Unit
) {
    val categories by viewModel.seriesCategories.collectAsState()
    val selectedCatId by viewModel.selectedSeriesCategoryId.collectAsState()
    val searchQuery by viewModel.seriesSearchQuery.collectAsState()
    val seriesList by viewModel.filteredSeriesStreams.collectAsState()

    TopographicBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // COLUNA 1: CATEGORIAS & BUSCA (Largura ~260dp)
            // ==========================================
            GlassCard(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                isFocusable = false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    // Campo de Busca
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.seriesSearchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x331C1F2B)),
                        placeholder = {
                            Text(
                                "Buscar série...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCoral,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Opção "Selecionar Todos"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedCatId == "all") Brush.horizontalGradient(listOf(Color(0x4DFF574D), Color(0x26FF574D)))
                                else Brush.horizontalGradient(listOf(Color(0x22FFFFFF), Color(0x22FFFFFF)))
                            )
                            .border(1.dp, if (selectedCatId == "all") AccentCoral else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectedSeriesCategoryId.value = "all" }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "★  Selecionar Todos",
                                color = if (selectedCatId == "all") AccentCoral else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (selectedCatId == "all") FontWeight.Bold else FontWeight.Medium
                            )
                            if (selectedCatId == "all") {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "GÊNEROS DE SÉRIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories.filter { it.categoryId != "all" }) { cat ->
                            SeriesCategoryRow(
                                category = cat,
                                isSelected = cat.categoryId == selectedCatId,
                                onClick = { viewModel.selectedSeriesCategoryId.value = cat.categoryId }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // COLUNA 2: GRID DE SÉRIES (Restante da tela)
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CATÁLOGO DE SÉRIES",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Temporadas completas, episódios em alta definição e lançamentos",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = "${seriesList.size} séries",
                        fontSize = 12.sp,
                        color = AccentCoral,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(seriesList) { series ->
                        SeriesPosterCard(
                            series = series,
                            onClick = {
                                val url = viewModel.repository.buildVodStreamUrl(series.seriesId, "mp4")
                                onPlaySeries(url, series.name)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesCategoryRow(
    category: SeriesCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isSelected -> Color(0x33FF574D)
        isFocused -> Color(0x26FFFFFF)
        else -> Color.Transparent
    }

    val border = when {
        isSelected -> AccentCoral
        isFocused -> Color.White.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = category.categoryName,
            color = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SeriesPosterCard(
    series: SeriesStream,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale = if (isFocused) 1.05f else 1.0f
    val borderColor = if (isFocused) AccentCoral else Color.White.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .scale(scale)
            .width(160.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .shadow(if (isFocused) 16.dp else 6.dp, RoundedCornerShape(16.dp), spotColor = AccentCoral)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141720))
                .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            if (!series.cover.isNullOrBlank()) {
                AsyncImage(
                    model = series.cover,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(series.name.take(2).uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 24.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                            startY = 140f
                        )
                    )
            )

            // Badge de Nota ★
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = RatingGold, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = series.rating ?: "8.6",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AccentCoral.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = series.name,
            color = if (isFocused) AccentCoral else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = series.genre ?: "Série Original",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
