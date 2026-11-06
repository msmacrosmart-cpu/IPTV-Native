package com.example.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.iptv.data.epg.EpgGuide
import com.example.iptv.data.model.EpgEntry
import com.example.iptv.data.model.LiveCategory
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.PlaybackRequest
import com.example.iptv.data.model.UserSettings
import com.example.iptv.player.PreviewPlayer
import com.example.iptv.ui.components.CenteredLoading
import com.example.iptv.ui.components.SunsetErrorState
import com.example.iptv.ui.components.GlassPanel
import com.example.iptv.ui.components.LiveBadge
import com.example.iptv.ui.components.ScreenHeader
import com.example.iptv.ui.components.SearchField
import com.example.iptv.ui.components.SunsetFocusable
import com.example.iptv.ui.components.SunsetProgressBar
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.components.formatClock
import com.example.iptv.ui.components.rememberNowMs
import com.example.iptv.ui.theme.SunsetColors
import com.example.iptv.viewmodel.MainTvViewModel

/** Reference layout, connected to the app's existing catalogue, session and Media3 player. */
@Composable
fun LiveTvScreen(viewModel: MainTvViewModel) {
    val categories by viewModel.liveCategories.collectAsState()
    val allChannels by viewModel.liveAll.collectAsState()
    val channels by viewModel.liveChannels.collectAsState()
    val loading by viewModel.liveLoading.collectAsState()
    val error by viewModel.liveError.collectAsState()
    val category by viewModel.liveCategoryFilter.collectAsState()
    val query by viewModel.liveQuery.collectAsState()
    val selected by viewModel.selectedChannel.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val guide by viewModel.epgGuide.collectAsState()
    val entries by viewModel.epgEntries.collectAsState()
    val epgLoading by viewModel.epgLoading.collectAsState()
    val epgError by viewModel.epgError.collectAsState()
    val now = rememberNowMs()
    val counts = remember(allChannels) { allChannels.groupingBy { it.categoryId }.eachCount() }

    TopographicBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 900.dp || maxHeight < 500.dp
            val sidePadding = if (compact) 14.dp else 28.dp
            val gap = if (compact) 8.dp else 16.dp
            Column(Modifier.fillMaxSize().padding(horizontal = sidePadding, vertical = if (compact) 12.dp else 24.dp)) {
                ScreenHeader("TV AO VIVO", "Navegue pelas categorias e pré-visualize os canais", compact)
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                when {
                    loading && allChannels.isEmpty() -> CenteredLoading()
                    error != null && allChannels.isEmpty() -> SunsetErrorState(error.orEmpty(), onRetry = { viewModel.loadLive(force = true) })
                    else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        CategoriesColumn(
                            categories = categories,
                            counts = counts,
                            total = allChannels.size,
                            selectedCategory = category,
                            query = query,
                            onQueryChange = { viewModel.liveQuery.value = it },
                            onSelect = viewModel::selectCategory,
                            modifier = Modifier.weight(0.24f)
                        )
                        ChannelsColumn(
                            channels = channels,
                            category = category,
                            selected = selected,
                            guide = guide,
                            selectedEntries = entries,
                            now = now,
                            onFocused = viewModel::onChannelFocused,
                            onOpen = viewModel::playChannelFullscreen,
                            onRefresh = { viewModel.loadLive(force = true) },
                            modifier = Modifier.weight(0.39f)
                        )
                        PreviewColumn(
                            selected = selected,
                            preview = preview,
                            settings = settings,
                            entries = entries,
                            loading = epgLoading,
                            error = epgError,
                            now = now,
                            onRetry = viewModel::retryEpg,
                            onOpen = viewModel::openPreviewFullscreen,
                            modifier = Modifier.weight(0.37f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoriesColumn(
    categories: List<LiveCategory>,
    counts: Map<String?, Int>,
    total: Int,
    selectedCategory: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier
) {
    GlassPanel(modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            SearchField(value = query, onValueChange = onQueryChange, placeholder = "Buscar canal…")
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(3.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "__all_channels__") {
                    CategoryPill("Todos os canais", total, selectedCategory == null) { onSelect(null) }
                }
                items(categories.distinctBy { it.id }, key = { "category:${it.id}" }) { category ->
                    CategoryPill(category.name, counts[category.id] ?: 0, category.id == selectedCategory) {
                        onSelect(category.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(name: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    SunsetFocusable(
        onClick = onSelect,
        onFocused = onSelect,
        selected = selected,
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                name, modifier = Modifier.weight(1f), color = SunsetColors.TextPrimary,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(count.toString(), color = SunsetColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelsColumn(
    channels: List<LiveChannel>,
    category: String?,
    selected: LiveChannel?,
    guide: EpgGuide,
    selectedEntries: List<EpgEntry>,
    now: Long,
    onFocused: (LiveChannel) -> Unit,
    onOpen: (LiveChannel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier
) {
    GlassPanel(modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${channels.size} canais", color = SunsetColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar canais", tint = SunsetColors.TextSecondary)
                }
            }
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum canal encontrado. Ajuste a busca ou a categoria.", color = SunsetColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
                }
            } else {
                key(category) {
                    LazyColumn(
                        Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(3.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(channels, key = { index, channel -> "${channel.id}:$index" }) { _, channel ->
                            val program = guide.forChannel(channel).firstOrNull { it.isCurrent(now) }
                                ?: selectedEntries.takeIf { selected?.id == channel.id }?.firstOrNull { it.isCurrent(now) || it.isNowPlaying }
                            ChannelRow(channel, selected?.id == channel.id, program, now,
                                onFocused = { onFocused(channel) }, onOpen = { onOpen(channel) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: LiveChannel, playing: Boolean, program: EpgEntry?, now: Long, onFocused: () -> Unit, onOpen: () -> Unit) {
    SunsetFocusable(
        onClick = onOpen, onFocused = onFocused, selected = playing,
        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(72.dp).testTag("channel-${channel.id}")
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (channel.streamNumber > 0) channel.streamNumber.toString() else "—", modifier = Modifier.width(27.dp), textAlign = TextAlign.Center,
                color = SunsetColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))) {
                AsyncImage(model = channel.icon, contentDescription = null, contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(Color.Transparent), error = ColorPainter(Color.Transparent), fallback = ColorPainter(Color.Transparent),
                    modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(channel.name, color = SunsetColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(program?.title ?: "Sem programação", color = SunsetColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (program != null) SunsetProgressBar(program.progress(now), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PreviewColumn(
    selected: LiveChannel?,
    preview: PlaybackRequest?,
    settings: UserSettings,
    entries: List<EpgEntry>,
    loading: Boolean,
    error: String?,
    now: Long,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier
) {
    val current = entries.firstOrNull { it.isCurrent(now) } ?: entries.firstOrNull { it.isNowPlaying }
    val upcoming = entries.filter { it.startMillis >= (current?.endMillis ?: now) }.sortedBy { it.startMillis }.take(3)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val maxPreviewHeight = maxHeight * 0.52f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SunsetFocusable(
                onClick = onOpen, enabled = preview != null, shape = RoundedCornerShape(20.dp), focusScale = 1.01f,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxPreviewHeight).aspectRatio(16f / 9f)
            ) { focused ->
                PreviewPlayer(request = preview, decoder = settings.decoder, bufferSeconds = settings.bufferSeconds, modifier = Modifier.fillMaxSize()) {
                    Text("Selecione um canal", color = SunsetColors.TextSecondary, fontSize = 13.sp)
                }
                if (focused && preview != null) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center) {
                        Text("OK para tela cheia", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            GlassPanel(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (selected == null) {
                        Text("Selecione um canal", color = SunsetColors.TextSecondary)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selected.name, modifier = Modifier.weight(1f).testTag("preview-channel"), color = SunsetColors.TextPrimary, fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LiveBadge()
                        }
                        when {
                            loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = SunsetColors.Coral, trackColor = Color(0x33FFFFFF))
                            error != null && current == null -> {
                                Text(error, color = SunsetColors.TextSecondary, fontSize = 12.sp)
                                SunsetFocusable(onClick = onRetry, shape = RoundedCornerShape(50)) {
                                    Text("Tentar novamente", color = SunsetColors.TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                }
                            }
                            current == null -> Text("Sem informação de programação", color = SunsetColors.TextSecondary, fontSize = 13.sp)
                            else -> {
                                Text(current.title, color = SunsetColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(formatClock(current.startMillis), color = SunsetColors.TextSecondary, fontSize = 11.sp)
                                    SunsetProgressBar(current.progress(now), Modifier.weight(1f))
                                    Text(formatClock(current.endMillis), color = SunsetColors.TextSecondary, fontSize = 11.sp)
                                }
                                if (current.description.isNotBlank()) Text(current.description, color = SunsetColors.TextSecondary, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (upcoming.isNotEmpty()) {
                            Text("A seguir", color = SunsetColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            upcoming.forEach { program ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(formatClock(program.startMillis), color = SunsetColors.Orange, fontSize = 11.sp)
                                    Text(program.title, color = SunsetColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
