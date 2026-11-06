package com.xtream.player.ui.live

import android.view.ViewGroup
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.xtream.player.data.*
import com.xtream.player.ui.browse.CatalogCategoryRow
import com.xtream.player.ui.components.IconAction
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.player.buildPlayer
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtClock
import com.xtream.player.util.initials
import kotlinx.coroutines.delay

@Composable
fun LiveScreen(content: ContentState, epg: Map<String, List<Programme>>, favorites: List<String>, query: String,
               onToggleFav: (String) -> Unit, onPlay: (PlayerRequest) -> Unit, previewEnabled: Boolean = true,
               onChannelFocused: (Channel) -> Unit = {}, restoreChannelId: String? = null, onChannelRestored: () -> Unit = {}) {
    var catId by rememberSaveable { mutableStateOf("all") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (previewEnabled) LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val channels = remember(content.channels, favorites.toList(), catId, query) {
        content.channels.filter {
            (catId == "all" || (catId == "fav" && "ch:${it.id}" in favorites) || it.categoryId == catId) && it.name.contains(query, true)
        }
    }
    val counts = remember(content.channels) { content.channels.groupingBy { it.categoryId }.eachCount() }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val inputMode = LocalInputModeManager.current
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(previewEnabled, restoreChannelId, channels) {
        if (previewEnabled && restoreChannelId != null) {
            val index = channels.indexOfFirst { it.id == restoreChannelId }
            if (index >= 0) {
                focusedId = restoreChannelId; previewId = restoreChannelId; listState.scrollToItem(index)
                delay(800)
                if (focusedId == restoreChannelId) runCatching { focusRequesters[restoreChannelId]?.requestFocus() }
                onChannelRestored()
            }
        }
    }
    LaunchedEffect(channels) {
        if (channels.none { it.id == previewId }) previewId = channels.firstOrNull()?.id
        if (channels.none { it.id == focusedId }) focusedId = null
    }
    LaunchedEffect(focusedId, channels) { focusedId?.let { id -> delay(450); if (channels.any { it.id == id }) previewId = id } }
    val preview = channels.firstOrNull { it.id == previewId }
    LaunchedEffect(preview?.id) { preview?.let(onChannelFocused) }
    var muted by rememberSaveable { mutableStateOf(true) }
    fun play(ch: Channel) {
        val index = channels.indexOf(ch).coerceAtLeast(0)
        onPlay(PlayerRequest(ch.name, ch.url, true, channels.ifEmpty { listOf(ch) }, index, ch.fallbackUrl))
    }
    @Composable fun ChannelList(modifier: Modifier) {
        val shape = RoundedCornerShape(14.dp)
        Column(modifier.testTag("tv-channel-pane").clip(shape).background(Color(0xD00D0F12)).border(1.dp, XColors.Line, shape)) {
            Text("${if (catId == "fav") "Favoritos" else "Canais"}  ·  ${channels.size}", color = XColors.Text,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))
            if (channels.isEmpty()) Text("Nenhum canal encontrado.", color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            else LazyColumn(state = listState, modifier = Modifier.testTag("channel-list"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(channels, key = { it.id }) { ch ->
                    val requester = remember(ch.id) { focusRequesters.getOrPut(ch.id) { FocusRequester() } }
                    ChannelRow(ch, epg[ch.epgId], now, "ch:${ch.id}" in favorites, previewId == ch.id,
                        Modifier.focusRequester(requester), onFocus = { focusedId = ch.id },
                        onFavorite = { onToggleFav("ch:${ch.id}") }) {
                        val wasPreviewing = previewId == ch.id
                        focusedId = ch.id; previewId = ch.id
                        // Touch selects the inline player first; a second tap or Fullscreen opens it.
                        // On TV, focus already previews the channel and OK opens fullscreen.
                        if (inputMode.inputMode != InputMode.Touch || wasPreviewing) play(ch)
                    }
                }
            }
        }
    }
    @Composable fun Preview(modifier: Modifier) {
        // Preserve the existing live preview, sound, fullscreen and EPG controls.
        PreviewPanel(preview, preview?.let { epg[it.epgId] }, now, muted, previewEnabled, false,
            modifier.testTag("tv-preview-pane"), onMute = { muted = !muted }, onPlay = { preview?.let(::play) })
    }
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.weight(.22f).fillMaxHeight().testTag("tv-categories-pane").clip(shape)
            .background(Color(0xD00D0F12)).border(1.dp, XColors.Line, shape).padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text("Categorias de TV", color = XColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            LazyColumn(Modifier.weight(1f).testTag("category-list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { CatalogCategoryRow("Todos", content.channels.size, catId == "all") { catId = "all" } }
                item { CatalogCategoryRow("Favoritos", favorites.count { it.startsWith("ch:") }, catId == "fav") { catId = "fav" } }
                items(content.liveCategories, key = { it.id }) { cat ->
                    CatalogCategoryRow(cat.name, counts[cat.id] ?: 0, catId == cat.id) { catId = cat.id }
                }
            }
        }
        ChannelList(Modifier.weight(.44f).fillMaxHeight())
        Preview(Modifier.weight(.34f).fillMaxHeight())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(ch: Channel, prog: List<Programme>?, now: Long, fav: Boolean, active: Boolean,
                       modifier: Modifier = Modifier, onFocus: () -> Unit, onFavorite: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val (cur, _) = prog.nowNext(now)
    val pct = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
    Row(modifier.fillMaxWidth().testTag("channel-${ch.id}").onFocusChanged { if (it.hasFocus) onFocus() }
        .focusGlow(shape, zoom = 1f).clip(shape).background(if (active) XColors.Selection else Color(0xB00C0E12))
        .border(1.dp, if (active) XColors.Accent.copy(alpha = .6f) else XColors.Line, shape)
        .combinedClickable(onClick = onClick, onLongClick = onFavorite).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(XColors.Panel), contentAlignment = Alignment.Center) {
            if (!ch.logo.isNullOrBlank()) AsyncImage(ch.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(5.dp))
            else Text(initials(ch.name), color = XColors.BlueSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("${ch.number}. ${ch.name}", color = if (active) XColors.BlueSoft else XColors.Text,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cur?.title ?: "Sem programação disponível", color = XColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (cur != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtClock(cur.start), color = XColors.Body, fontSize = 10.sp)
                Box(Modifier.weight(1f).padding(horizontal = 7.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color(0x4DFFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(pct).background(XColors.Accent))
                }
                Text(fmtClock(cur.stop), color = XColors.Body, fontSize = 10.sp)
            }
        }
        IconAction(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
            if (fav) "Remover favorito ${ch.name}" else "Favoritar ${ch.name}", onClick = onFavorite)
    }
}

@Composable
private fun PreviewPanel(channel: Channel?, programs: List<Programme>?, now: Long, muted: Boolean,
                         enabled: Boolean, compact: Boolean, modifier: Modifier = Modifier,
                         onMute: () -> Unit, onPlay: () -> Unit) {
    val upcoming = programs.orEmpty().filter { it.stop > now }.sortedBy { it.start }.take(if (compact) 2 else 8)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Color(0xD00D0F12)).border(1.dp, XColors.Line, shape)
        .then(if (compact) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (compact) 2.2f else 16f / 9f).background(Color.Black)) {
            if (enabled && channel != null) PreviewPlayer(channel.url, channel.fallbackUrl, muted)
            else Text("Selecione um canal", color = XColors.TextDim, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
        }
        if (channel != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${channel.number} · ${channel.name}", color = XColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("preview-channel"))
                    if (compact) {
                        Text(upcoming.firstOrNull()?.title ?: "Sem programação disponível", color = XColors.TextDim,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        upcoming.getOrNull(1)?.let { Text("A seguir: ${it.title}", color = XColors.Body,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                IconAction(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (muted) "Ativar som" else "Silenciar", onClick = onMute)
                IconAction(Icons.Filled.Fullscreen, "Tela cheia", onClick = onPlay)
            }
            if (!compact) {
                Text("Programação", color = XColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp))
                if (upcoming.isEmpty()) Text("Sem programação disponível.", color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                upcoming.forEach { p ->
                    val live = now in p.start until p.stop
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (live) XColors.Selection else XColors.Panel).padding(14.dp)) {
                        Text("${fmtClock(p.start)} – ${fmtClock(p.stop)}" + if (live) "   AO VIVO" else "", color = if (live) XColors.Live else XColors.TextDim, fontSize = 12.sp)
                        Text(p.title, color = if (live) XColors.BlueSoft else XColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
@AndroidXOptIn(UnstableApi::class)
@Composable
private fun PreviewPlayer(url: String, fallbackUrl: String?, muted: Boolean) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { buildPlayer(ctx, handleAudioFocus = false).apply { volume = 0f; playWhenReady = true } }
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
    LaunchedEffect(muted) {
        player.volume = if (muted) 0f else 1f
        player.setAudioAttributes(AudioAttributes.DEFAULT, !muted)
    }
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
