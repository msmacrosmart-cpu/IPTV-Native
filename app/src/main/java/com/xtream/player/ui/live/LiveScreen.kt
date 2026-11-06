package com.xtream.player.ui.live

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.ViewGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
@AndroidXOptIn(UnstableApi::class)
@Composable
fun LiveScreen(
    content: ContentState,
    epg: Map<String, List<Programme>>,
    favorites: List<String>,
    query: String,
    onToggleFav: (String) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    previewEnabled: Boolean = true,
    onChannelFocused: (Channel) -> Unit = {},
) {
    var catId by rememberSaveable { mutableStateOf("all") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (previewEnabled) LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val channels = remember(content.channels, favorites.toList(), catId, query) {
        content.channels.filter {
            (catId == "all" || (catId == "fav" && "ch:${it.id}" in favorites) || it.categoryId == catId) &&
                it.name.contains(query, true)
        }
    }
    val categoryCounts = remember(content.channels) { content.channels.groupingBy { it.categoryId }.eachCount() }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val restoreFocus = remember { FocusRequester() }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(channels, focusedId) {
        val index = channels.indexOfFirst { it.id == focusedId }
        if (!restored && index >= 0) {
            listState.scrollToItem(index)
            withFrameNanos { }
            runCatching { restoreFocus.requestFocus() }
            restored = true
        }
    }
    // A refreshed catalog or a filter may remove the selected channel.
    LaunchedEffect(channels) {
        if (channels.none { it.id == previewId }) previewId = channels.firstOrNull()?.id
        if (channels.none { it.id == focusedId }) focusedId = null
    }
    LaunchedEffect(focusedId, channels) {
        focusedId?.let { id -> delay(450); if (channels.any { it.id == id }) previewId = id }
    }
    val preview = channels.firstOrNull { it.id == previewId }
    LaunchedEffect(preview?.id) { preview?.let(onChannelFocused) }
    var muted by rememberSaveable { mutableStateOf(true) }

    // Keep the reference's three columns; allow them to fit on smaller landscape devices.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = (maxWidth - 80.dp).coerceAtLeast(0.dp)
        val categoryWidth = minOf(210.dp, available * 0.26f)
        val previewWidth = minOf(380.dp, available * 0.40f)

    Row(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // ---- Coluna 1: categorias
        Column(Modifier.width(categoryWidth).fillMaxHeight()) {
            Text("TV AO VIVO", color = XColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.testTag("category-list"), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                item { CatRow("Todos", content.channels.size, catId == "all") { catId = "all" } }
                item { CatRow("Favoritos", favorites.count { it.startsWith("ch:") }, catId == "fav") { catId = "fav" } }
                items(content.liveCategories, key = { it.id }) { c ->
                    CatRow(c.name, (categoryCounts[c.id] ?: 0), catId == c.id) { catId = c.id }
                }
            }
        }
        // ---- Coluna 2: canais + EPG
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("Canais  ·  ${channels.size}", color = XColors.TextDim, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(state = listState, modifier = Modifier.testTag("channel-list"), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(channels, key = { it.id }) { ch ->
                    ChannelRow(
                        ch, epg[ch.epgId], now, "ch:${ch.id}" in favorites, previewId == ch.id,
                        modifier = if (ch.id == focusedId) Modifier.focusRequester(restoreFocus) else Modifier,
                        onFocus = { focusedId = ch.id },
                        onLongClick = { onToggleFav("ch:${ch.id}") },
                    ) { onPlay(PlayerRequest(ch.name, ch.url, true, channels, channels.indexOf(ch))) }
                }
            }
        }
        // ---- Coluna 3: preview
        Column(Modifier.width(previewWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
            GlassPanel(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(18.dp)) {
                if (previewEnabled && preview != null) PreviewPlayer(preview.url, preview.fallbackUrl, muted)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2A44), Color(0xFF0B1220)))))
                Row(Modifier.padding(12.dp).clip(RoundedCornerShape(6.dp)).background(XColors.Live).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (preview != null) {
                val (cur, next) = epg[preview.epgId].nowNext(now)
                Text("${preview.number}  ·  ${preview.name}", color = XColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("preview-channel"))
                Spacer(Modifier.height(8.dp))
                Text("AGORA", color = XColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(cur?.let { "${it.title}  (${fmtClock(it.start)}–${fmtClock(it.stop)})" } ?: "Sem informação de guia", color = XColors.Text, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("A SEGUIR", color = XColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(next?.let { "${it.title}  (${fmtClock(it.start)})" } ?: "—", color = XColors.TextDim, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    modifier: Modifier = Modifier, onFocus: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val (cur, next) = prog.nowNext(now)
    val pct = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) } ?: 0f
    Row(
        modifier.fillMaxWidth().testTag("channel-${ch.id}").onFocusChanged { if (it.hasFocus) onFocus() }
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

@AndroidXOptIn(UnstableApi::class)
@Composable
private fun PreviewPlayer(url: String, fallbackUrl: String?, muted: Boolean) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { buildPlayer(ctx).apply { volume = 0f; playWhenReady = true } }
    var triedFallback by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    DisposableEffect(player, url, fallbackUrl) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!triedFallback && !fallbackUrl.isNullOrBlank() && fallbackUrl != url) {
                    triedFallback = true
                    player.setMediaItem(MediaItem.fromUri(fallbackUrl))
                    player.prepare()
                    player.playWhenReady = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                } else failed = true
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) failed = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (!failed) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(url) { player.setMediaItem(MediaItem.fromUri(url)); player.prepare() }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c -> PlayerView(c).apply {
                useController = false; this.player = player
                isFocusable = false; isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            } },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) Text("Não foi possível reproduzir o canal.", color = XColors.Text,
            fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(12.dp))
    }
}
