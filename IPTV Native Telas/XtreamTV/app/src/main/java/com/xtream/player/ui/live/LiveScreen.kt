package com.xtream.player.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.xtream.player.data.*
import com.xtream.player.ui.components.*
import com.xtream.player.ui.player.buildPlayer
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtClock
import com.xtream.player.util.initials
import kotlinx.coroutines.delay

/** TV ao vivo em 3 colunas: Categorias | Canais + EPG | Preview em tempo real (Media3). */
@OptIn(UnstableApi::class)
@Composable
fun LiveScreen(
    content: ContentState,
    epg: Map<String, List<Programme>>,
    favorites: List<String>,
    query: String,
    onToggleFav: (String) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    previewEnabled: Boolean = true,
) {
    var catId by remember { mutableStateOf("all") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (previewEnabled) LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val channels = content.channels.filter {
        (catId == "all" || (catId == "fav" && "ch:${it.id}" in favorites) || it.categoryId == catId) &&
            it.name.contains(query, true)
    }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf(content.channels.firstOrNull()?.id) }
    LaunchedEffect(focusedId) { focusedId?.let { delay(450); previewId = it } }   // debounce do preview
    val preview = content.channels.firstOrNull { it.id == previewId }
    var muted by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // ---- Coluna 1: categorias
        Column(Modifier.width(210.dp).fillMaxHeight()) {
            Text("TV AO VIVO", color = XColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                item { CatRow("Todos", content.channels.size, catId == "all") { catId = "all" } }
                item { CatRow("Favoritos", favorites.count { it.startsWith("ch:") }, catId == "fav") { catId = "fav" } }
                items(content.liveCategories, key = { it.id }) { c ->
                    CatRow(c.name, content.channels.count { it.categoryId == c.id }, catId == c.id) { catId = c.id }
                }
            }
        }
        // ---- Coluna 2: canais + EPG
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("Canais  ·  ${channels.size}", color = XColors.TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(channels, key = { it.id }) { ch ->
                    ChannelRow(
                        ch, epg[ch.epgId], now, "ch:${ch.id}" in favorites, previewId == ch.id,
                        onFocus = { focusedId = ch.id },
                        onLongClick = { onToggleFav("ch:${ch.id}") },
                    ) { onPlay(PlayerRequest(ch.name, ch.url, true, channels, channels.indexOf(ch))) }
                }
            }
        }
        // ---- Coluna 3: preview
        Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            GlassPanel(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(18.dp)) {
                if (previewEnabled && preview != null) PreviewPlayer(preview.url, muted)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2A44), Color(0xFF0B1220)))))
                Row(Modifier.padding(12.dp).clip(RoundedCornerShape(6.dp)).background(XColors.Live).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (preview != null) {
                val (cur, next) = epg[preview.epgId].nowNext(now)
                Text("${preview.number}  ·  ${preview.name}", color = XColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text("AGORA", color = XColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(cur?.let { "${it.title}  (${fmtClock(it.start)}–${fmtClock(it.stop)})" } ?: "Sem informação de guia", color = XColors.Text, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("A SEGUIR", color = XColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(next?.let { "${it.title}  (${fmtClock(it.start)})" } ?: "—", color = XColors.TextDim, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlowButton("Tela cheia", Icons.Filled.Fullscreen) {
                        val idx = channels.indexOf(preview).coerceAtLeast(0)
                        onPlay(PlayerRequest(preview.name, preview.url, true, channels.ifEmpty { listOf(preview) }, idx))
                    }
                    GlowButton(if (muted) "Som" else "Mudo", if (muted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, ringAlways = false) { muted = !muted }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().focusGlow(shape, zoom = 1.02f).clip(shape)
            .background(if (selected) Color(0x33F0564A) else Color(0x14FFFFFF))
            .combinedClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = if (selected) XColors.Text else Color(0xFFD0D0D5), fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", color = XColors.TextDim, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    ch: Channel, prog: List<Programme>?, now: Long, fav: Boolean, active: Boolean,
    onFocus: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val (cur, next) = prog.nowNext(now)
    val pct = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) } ?: 0f
    Row(
        Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocus() }
            .focusGlow(shape, zoom = 1.015f).clip(shape)
            .background(if (active) Color(0x26FFFFFF) else Color(0x14FFFFFF))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${ch.number}", color = XColors.TextDim, fontSize = 15.sp, modifier = Modifier.width(34.dp))
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF5B3FD0), Color(0xFF2A1A66)))),
            contentAlignment = Alignment.Center,
        ) { Text(initials(ch.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(ch.name, color = XColors.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (cur?.title ?: "Sem guia") + (next?.let { "   ›   ${it.title}" } ?: ""),
                color = XColors.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(Brush.horizontalGradient(listOf(XColors.Accent, XColors.Gold))))
            }
        }
        if (fav) Icon(Icons.Filled.Star, null, tint = XColors.Gold, modifier = Modifier.padding(start = 10.dp).size(20.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPlayer(url: String, muted: Boolean) {
    val ctx = LocalContext.current
    val player = remember { buildPlayer(ctx).apply { playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(url) { player.setMediaItem(MediaItem.fromUri(url)); player.prepare() }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    AndroidView(
        factory = { c -> PlayerView(c).apply { useController = false; this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } },
        modifier = Modifier.fillMaxSize(),
    )
}
