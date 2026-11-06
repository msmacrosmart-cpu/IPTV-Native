package com.example.iptv.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.iptv.data.model.LiveCategory
import com.example.iptv.data.model.LiveStream
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.*
import com.example.iptv.viewmodel.MainTvViewModel

/**
 * Tela de TV ao Vivo em 3 Colunas inspirada no IPTV Smarters Pro:
 * - Coluna 1: Categorias de Canais + Botão 'Selecionar Todos' + Campo de Busca
 * - Coluna 2: Lista de Canais com Logomarca + Nome + EPG em tempo real com barra de progresso
 * - Coluna 3 / Preview: Media3 ExoPlayer com vídeo ao vivo + Card com detalhes completos do EPG
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    viewModel: MainTvViewModel,
    onFullScreen: (url: String, title: String) -> Unit
) {
    val categories by viewModel.liveCategories.collectAsState()
    val selectedCatId by viewModel.selectedLiveCategoryId.collectAsState()
    val searchQuery by viewModel.liveSearchQuery.collectAsState()
    val channels by viewModel.filteredLiveStreams.collectAsState()
    val selectedChannel by viewModel.selectedLiveStream.collectAsState()
    val streamUrl by viewModel.livePlayerUrl.collectAsState()

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
                    // Barra de Pesquisa de Canais/Categorias
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.liveSearchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x331C1F2B)),
                        placeholder = {
                            Text(
                                "Buscar canal...",
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

                    // Botão / Chip de Destaque 'Selecionar Todos'
                    SelectAllCategoryChip(
                        isSelected = selectedCatId == "all",
                        onClick = { viewModel.selectedLiveCategoryId.value = "all" }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "CATEGORIAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    // Lista de Categorias
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories.filter { it.categoryId != "all" }) { category ->
                            CategoryItemRow(
                                category = category,
                                isSelected = category.categoryId == selectedCatId,
                                onClick = { viewModel.selectedLiveCategoryId.value = category.categoryId }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // COLUNA 2: LISTA DE CANAIS + EPG (Largura ~340dp)
            // ==========================================
            GlassCard(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                isFocusable = false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CANAIS DISPONÍVEIS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Text(
                            text = "${channels.size} canais",
                            fontSize = 11.sp,
                            color = AccentCoral
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels) { channel ->
                            ChannelItemCard(
                                channel = channel,
                                isSelected = channel.streamId == selectedChannel?.streamId,
                                onClick = { viewModel.selectLiveChannel(channel) }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // COLUNA 3: PREVIEW EXOPLAYER + DETALHES EPG
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tela de Pré-Visualização de Vídeo (ExoPlayer)
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.35f),
                    shape = RoundedCornerShape(24.dp),
                    isFocusable = false
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (streamUrl != null) {
                            LiveExoPlayerView(streamUrl = streamUrl!!)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0F1116)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.LiveTv,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Selecione um canal para pré-visualizar",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Badge 'AO VIVO / LIVE'
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC000000))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(LiveRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Botão de Tela Cheia (Fullscreen)
                        IconButton(
                            onClick = {
                                selectedChannel?.let { ch ->
                                    val url = streamUrl ?: viewModel.repository.buildLiveStreamUrl(ch.streamId)
                                    onFullScreen(url, ch.name)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x80000000))
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Tela Cheia",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Card de Informações Detalhadas do EPG (Guia de Programação)
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    isFocusable = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedChannel?.name ?: "Selecione um canal",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "14:00 - 15:30 (Agora)",
                                    fontSize = 12.sp,
                                    color = AccentCoral,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Transmissão ao Vivo • Resolução FHD 1080p • H.264 / AAC",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Barra de Progresso do Programa Atual
                            Column {
                                LinearProgressIndicator(
                                    progress = { 0.65f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = AccentCoral,
                                    trackColor = Color(0x33FFFFFF)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Decorrido: 58 min", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                                    Text("Restante: 32 min", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Sinopse do EPG: Cobertura completa das principais notícias do Brasil e do mundo, análises esportivas em tempo real e boletins meteorológicos ao vivo.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Próximo Programa na Grade
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x331F2330))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "A SEGUIR: Cine Premium Especial",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "15:30",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chip para Selecionar Todos / Todas as Categorias.
 */
@Composable
private fun SelectAllCategoryChip(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Brush.horizontalGradient(listOf(Color(0x4DFF574D), Color(0x26FF574D)))
                else Brush.horizontalGradient(listOf(Color(0x26FFFFFF), Color(0x14FFFFFF)))
            )
            .border(
                1.dp,
                if (isSelected) AccentCoral else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
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
                color = if (isSelected) AccentCoral else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = AccentCoral,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Linha de categoria individual com suporte a foco D-pad.
 */
@Composable
private fun CategoryItemRow(
    category: LiveCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = when {
        isSelected -> Color(0x33FF574D)
        isFocused -> Color(0x22FFFFFF)
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
            .height(42.dp)
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

/**
 * Card do canal com logomarca, nome, EPG atual e barra de progresso.
 */
@Composable
private fun ChannelItemCard(
    channel: LiveStream,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isSelected -> AccentCoral
        isFocused -> Color.White
        else -> Color.White.copy(alpha = 0.08f)
    }

    val cardBg = when {
        isSelected -> Color(0x40FF574D)
        isFocused -> Color(0x333A4050)
        else -> Color(0x261A1D27)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(if (isSelected || isFocused) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo do Canal
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF13151D))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.streamIcon.isNullOrBlank()) {
                AsyncImage(
                    model = channel.streamIcon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = channel.name.take(3).uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Nome do Canal + EPG Atual
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Programa ao Vivo • Em andamento",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Barra sutil de progresso do EPG
            LinearProgressIndicator(
                progress = { 0.45f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = if (isSelected) AccentCoral else Color(0x66FF574D),
                trackColor = Color(0x22FFFFFF)
            )
        }
    }
}

/**
 * Visualizador AndroidView integrado ao Media3 ExoPlayer.
 */
@OptIn(UnstableApi::class)
@Composable
private fun LiveExoPlayerView(streamUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(streamUrl) {
        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
