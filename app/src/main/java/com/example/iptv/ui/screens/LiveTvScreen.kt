package com.example.iptv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iptv.data.model.EpgEntry
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.player.PreviewPlayer
import com.example.iptv.ui.components.CategoryRow
import com.example.iptv.ui.components.ChannelLogo
import com.example.iptv.ui.components.EmptyState
import com.example.iptv.ui.components.ErrorState
import com.example.iptv.ui.components.GlassCard
import com.example.iptv.ui.components.LoadingState
import com.example.iptv.ui.components.PrimaryButton
import com.example.iptv.ui.components.SearchField
import com.example.iptv.ui.components.SelectAllChip
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.LiveRed
import com.example.iptv.ui.theme.TextMuted
import com.example.iptv.ui.theme.TextPrimary
import com.example.iptv.ui.theme.TextSecondary
import com.example.iptv.util.FormatUtils
import com.example.iptv.viewmodel.MainTvViewModel
import kotlinx.coroutines.delay

/**
 * Live TV in three columns (IPTV Smarters style):
 *  1. categories + search;
 *  2. channel list (logo, name, number);
 *  3. Media3 preview player + full EPG card of the selected channel.
 */
@Composable
fun LiveTvScreen(viewModel: MainTvViewModel) {
    val categories by viewModel.liveCategories.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val error by viewModel.liveError.collectAsState()
    val categoryFilter by viewModel.liveCategoryFilter.collectAsState()
    val query by viewModel.liveQuery.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val epgEntries by viewModel.epgEntries.collectAsState()
    val epgLoading by viewModel.epgLoading.collectAsState()
    val epgError by viewModel.epgError.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allChannels by viewModel.liveAll.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }

    TopographicBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------------------------------------------------- column 1
            GlassCard(
                modifier = Modifier.width(250.dp).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                ) {
                    SearchField(
                        value = query,
                        onValueChange = { viewModel.liveQuery.value = it },
                        placeholder = "Buscar canal…"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SelectAllChip(
                        label = "Todos os canais (${allChannels.size})",
                        isSelected = categoryFilter == null,
                        onClick = { viewModel.selectCategory(null) }
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
                                    onClick = { viewModel.selectCategory(category.id) }
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------- column 2
            GlassCard(
                modifier = Modifier.width(330.dp).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${channels.size} canais",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { viewModel.loadLive(force = true) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar canais",
                                tint = TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    when {
                        loading && channels.isEmpty() -> LoadingState(
                            message = "Carregando canais…",
                            modifier = Modifier.weight(1f)
                        )

                        error != null && channels.isEmpty() -> ErrorState(
                            message = error.orEmpty(),
                            onRetry = { viewModel.loadLive(force = true) },
                            modifier = Modifier.weight(1f)
                        )

                        channels.isEmpty() -> EmptyState(
                            title = "Nenhum canal encontrado",
                            message = "Ajuste a busca ou selecione outra categoria.",
                            icon = Icons.Default.LiveTv,
                            modifier = Modifier.weight(1f)
                        )

                        else -> LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(channels, key = { it.id }) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    isSelected = channel.id == selectedChannel?.id,
                                    onClick = { viewModel.selectChannel(channel) },
                                    onLongClickPlay = { viewModel.playChannelFullscreen(channel) }
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------- column 3
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().weight(1.25f),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        PreviewPlayer(
                            request = preview,
                            decoder = settings.decoder,
                            bufferSeconds = settings.bufferSeconds,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.LiveTv,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Selecione um canal para pré-visualizar",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (selectedChannel != null) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(7.dp).clip(CircleShape).background(LiveRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedChannel?.name.orEmpty(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { viewModel.openPreviewFullscreen() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99000000))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Tela cheia",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth().weight(0.95f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    EpgPanel(
                        channel = selectedChannel,
                        entries = epgEntries,
                        isLoading = epgLoading,
                        error = epgError,
                        now = now,
                        onRetry = viewModel::retryEpg,
                        onFullscreen = { selectedChannel?.let { viewModel.playChannelFullscreen(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClickPlay: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isSelected -> AccentCoral
        isFocused -> Color.White.copy(alpha = 0.75f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    val background = when {
        isSelected -> Color(0x33FF6B5B)
        isFocused -> Color(0x332A2F3D)
        else -> Color(0x1A161922)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .border(if (isSelected || isFocused) 1.6.dp else 1.dp, borderColor, RoundedCornerShape(13.dp))
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
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (channel.streamNumber > 0) append("#").append(channel.streamNumber)
                    if (channel.hasArchive) {
                        if (isNotEmpty()) append("  ·  ")
                        append("ARQUIVO")
                    }
                    if (isEmpty()) append("Ao vivo")
                },
                color = TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1
            )
        }

        if (isSelected || isFocused) {
            IconButton(onClick = onLongClickPlay, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Assistir em tela cheia",
                    tint = AccentCoral,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun EpgPanel(
    channel: LiveChannel?,
    entries: List<EpgEntry>,
    isLoading: Boolean,
    error: String?,
    now: Long,
    onRetry: () -> Unit,
    onFullscreen: () -> Unit
) {
    if (channel == null) {
        EmptyState(
            title = "Nenhum canal selecionado",
            message = "Escolha um canal na lista para ver a programação e a pré-visualização.",
            icon = Icons.Default.Schedule,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val current = entries.firstOrNull { it.isCurrent(now) }
        ?: entries.firstOrNull { it.isNowPlaying }
        ?: entries.firstOrNull()
    val next = entries.firstOrNull { it.startMillis >= (current?.endMillis ?: now) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isLoading -> "Carregando EPG…"
                        current != null -> "${FormatUtils.formatTime(current.startMillis)} – ${FormatUtils.formatTime(current.endMillis)}"
                        else -> "Sem grade de programação"
                    },
                    color = AccentCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            PrimaryButton(
                label = "Assistir",
                onClick = onFullscreen,
                modifier = Modifier.width(124.dp),
                icon = Icons.Default.PlayArrow
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isLoading -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = AccentCoral,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            error != null -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error,
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recarregar EPG", tint = AccentCoral, modifier = Modifier.size(15.dp))
                }
            }

            current != null -> {
                Text(
                    text = current.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                val progress = current.progress(now)
                val animated by animateFloatAsState(targetValue = progress, label = "epgProgress")
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AccentCoral,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${((progress * 100).toInt()).coerceIn(0, 100)}% decorrido",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Termina às ${FormatUtils.formatTime(current.endMillis)}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                if (current.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = current.description,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            else -> Text(
                text = "Este painel não enviou informações de EPG para este canal.",
                color = TextMuted,
                fontSize = 11.5.sp
            )
        }

        next?.let { following ->
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x261F2330))
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A SEGUIR · ${following.title}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = FormatUtils.formatTime(following.startMillis),
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}
