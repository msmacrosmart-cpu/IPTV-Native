package com.sunsettv.app.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sunsettv.app.domain.EpgProgram
import com.sunsettv.app.domain.LiveChannel
import com.sunsettv.app.ui.components.CenteredLoading
import com.sunsettv.app.ui.components.ErrorState
import com.sunsettv.app.ui.components.GlassPanel
import com.sunsettv.app.ui.components.LiveBadge
import com.sunsettv.app.ui.components.ScreenHeader
import com.sunsettv.app.ui.components.SunsetFocusable
import com.sunsettv.app.ui.components.SunsetProgressBar
import com.sunsettv.app.ui.components.formatClock
import com.sunsettv.app.ui.components.rememberNowMs
import com.sunsettv.app.ui.theme.SunsetColors

/**
 * Aba "TV ao Vivo": 3 colunas (Categorias | Canais + EPG | Preview ao vivo).
 * Renderiza só a ÁREA DE CONTEÚDO — o menu lateral de ícones continua sendo o que você já tem.
 *
 * D-pad: ↑↓ percorre a lista; → / ← muda de coluna; OK em um canal abre [onOpenPlayer];
 * OK no preview também abre em tela cheia. O foco de cada lista é restaurado ao voltar.
 */
@Composable
fun LiveTvScreen(
    onOpenPlayer: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
    vm: LiveTvViewModel = viewModel(factory = LiveTvViewModel.Factory),
) {
    val state by vm.state.collectAsState()
    val epgVersion by vm.epgVersion.collectAsState()
    val now = rememberNowMs()

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 20.dp),
    ) {
        ScreenHeader("TV AO VIVO", "Navegue pelas categorias e pré-visualize os canais")
        Spacer(Modifier.height(16.dp))
        when {
            state.loading -> CenteredLoading()
            state.error != null -> ErrorState(state.error.orEmpty(), onRetry = vm::load)
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoriesColumn(
                    state = state,
                    onSelect = vm::selectCategory,
                    modifier = Modifier.weight(0.22f),
                )
                ChannelsColumn(
                    state = state,
                    epgVersion = epgVersion,
                    now = now,
                    currentProgram = vm::currentProgram,
                    onFocused = vm::onChannelFocused,
                    onOpen = onOpenPlayer,
                    modifier = Modifier.weight(0.40f),
                )
                PreviewColumn(
                    state = state,
                    now = now,
                    onOpen = onOpenPlayer,
                    modifier = Modifier.weight(0.38f),
                )
            }
        }
    }
}

// ───────────────────────── Coluna 1: Categorias ─────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoriesColumn(state: LiveTvUiState, onSelect: (String) -> Unit, modifier: Modifier) {
    GlassPanel(modifier.fillMaxHeight()) {
        LazyColumn(
            Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = ALL_CATEGORIES) {
                CategoryPill("Todos os canais", state.counts[ALL_CATEGORIES] ?: 0, state.selectedCategoryId == ALL_CATEGORIES) {
                    onSelect(ALL_CATEGORIES)
                }
            }
            items(state.categories, key = { it.id }) { cat ->
                CategoryPill(cat.name, state.counts[cat.id] ?: 0, state.selectedCategoryId == cat.id) { onSelect(cat.id) }
            }
        }
    }
}

@Composable
private fun CategoryPill(name: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    SunsetFocusable(
        onClick = onSelect,
        onFocused = onSelect, // no D-pad, focar já filtra a lista
        selected = selected,
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                color = SunsetColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(count.toString(), color = SunsetColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

// ───────────────────── Coluna 2: Canais com EPG ─────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelsColumn(
    state: LiveTvUiState,
    epgVersion: Int,
    now: Long,
    currentProgram: (LiveChannel, Long) -> EpgProgram?,
    onFocused: (LiveChannel) -> Unit,
    onOpen: (LiveChannel) -> Unit,
    modifier: Modifier,
) {
    GlassPanel(modifier.fillMaxHeight()) {
        if (state.channels.isEmpty()) {
            Text(
                "Nenhum canal nesta categoria",
                color = SunsetColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // key() recria a lista ao trocar de categoria (volta ao topo)
            key(state.selectedCategoryId) {
                LazyColumn(
                    Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.channels, key = { it.streamId }) { ch ->
                        ChannelRow(
                            channel = ch,
                            playing = ch.streamId == state.previewChannel?.streamId,
                            epgVersion = epgVersion,
                            now = now,
                            currentProgram = currentProgram,
                            onFocused = { onFocused(ch) },
                            onOpen = { onOpen(ch) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    playing: Boolean,
    epgVersion: Int,
    now: Long,
    currentProgram: (LiveChannel, Long) -> EpgProgram?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
) {
    val program = remember(epgVersion, now, channel.streamId) { currentProgram(channel, now) }
    SunsetFocusable(
        onClick = onOpen,
        onFocused = onFocused,
        selected = playing,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(76.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                channel.number.toString(),
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
                color = SunsetColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)),
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(Color.Transparent),
                    error = ColorPainter(Color.Transparent),
                    fallback = ColorPainter(Color.Transparent),
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    channel.name,
                    color = SunsetColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    program?.title ?: "Sem informação de programação",
                    color = SunsetColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (program != null) {
                    SunsetProgressBar(program.progressAt(now), Modifier.fillMaxWidth().height(3.dp))
                }
            }
        }
    }
}

// ─────────────── Coluna 3: Preview ao vivo + detalhes do EPG ───────────────

@Composable
private fun PreviewColumn(
    state: LiveTvUiState,
    now: Long,
    onOpen: (LiveChannel) -> Unit,
    modifier: Modifier,
) {
    val focused = state.focusedChannel
    val programs = state.programs
    val current = programs.firstOrNull { it.isLiveAt(now) }
    val upcoming = programs.filter { it.startMs >= (current?.endMs ?: now) }.take(3)

    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SunsetFocusable(
            onClick = { focused?.let(onOpen) },
            shape = RoundedCornerShape(20.dp),
            focusScale = 1.01f,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) { hasFocus ->
            LivePreviewPlayer(url = state.previewChannel?.streamUrl, modifier = Modifier.fillMaxSize())
            if (hasFocus) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("OK para tela cheia", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        GlassPanel(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (focused == null) {
                    Text("Selecione um canal", color = SunsetColors.TextSecondary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            focused.name,
                            modifier = Modifier.weight(1f, fill = false),
                            color = SunsetColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LiveBadge()
                    }
                    if (current == null) {
                        Text("Sem informação de programação", color = SunsetColors.TextSecondary, fontSize = 14.sp)
                    } else {
                        Text(
                            current.title,
                            color = SunsetColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(formatClock(current.startMs), color = SunsetColors.TextSecondary, fontSize = 12.sp)
                            SunsetProgressBar(current.progressAt(now), Modifier.weight(1f))
                            Text(formatClock(current.endMs), color = SunsetColors.TextSecondary, fontSize = 12.sp)
                        }
                        if (current.description.isNotBlank()) {
                            Text(
                                current.description,
                                color = SunsetColors.TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        Text("A seguir", color = SunsetColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        upcoming.forEach { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(formatClock(p.startMs), color = SunsetColors.Orange, fontSize = 12.sp)
                                Text(
                                    p.title,
                                    color = SunsetColors.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
