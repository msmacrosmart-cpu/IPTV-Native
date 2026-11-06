package com.xtream.player.ui.multi

import android.view.ViewGroup
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes as AndroidAudioAttributes
import android.os.Build
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.xtream.player.data.Channel
import com.xtream.player.data.PlayerRequest
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.IconAction
import com.xtream.player.ui.components.PillField
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.player.buildPlayer
import com.xtream.player.ui.theme.XColors
import kotlinx.coroutines.delay

/** Four independent players. Only one tile holds audio focus and is audible at a time. */
@Composable
fun MultiScreen(channels: List<Channel>, slotIds: List<String?>, audible: Int, enabled: Boolean,
                onSetSlot: (Int, String?) -> Unit, onAudible: (Int) -> Unit, onPlay: (PlayerRequest) -> Unit) {
    var picking by remember { mutableStateOf<Int?>(null) }
    var pendingFocus by remember { mutableStateOf<Int?>(null) }
    var focusedTile by remember { mutableStateOf<Int?>(null) }
    val requests = remember { List(4) { FocusRequester() } }
    val inputMode = LocalInputModeManager.current
    val view = LocalView.current
    val hasPlayers = slotIds.any { id -> id != null && channels.any { it.id == id } }
    val audioAllowed = rememberMultiAudioFocus(enabled && hasPlayers)
    LaunchedEffect(pendingFocus, picking, slotIds) {
        val index = pendingFocus ?: return@LaunchedEffect
        if (picking != null) return@LaunchedEffect
        if (inputMode.inputMode != InputMode.Touch) {
            // A dialog is a separate window. Wait for the main window to regain focus,
            // and do not let an older restoration overwrite a newer channel selection.
            repeat(20) {
                delay(75)
                if (pendingFocus != index || picking != null) return@LaunchedEffect
                if (inputMode.inputMode == InputMode.Touch) {
                    pendingFocus = null
                    return@LaunchedEffect
                }
                if (view.hasWindowFocus()) {
                    runCatching { requests[index].requestFocus() }
                    if (focusedTile == index) {
                        pendingFocus = null
                        return@LaunchedEffect
                    }
                }
            }
        }
        if (pendingFocus == index) pendingFocus = null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Three 44 dp player actions need enough room; stack videos on narrow phones.
        val columns = if (maxWidth < 448.dp) 1 else 2
        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("Multitela", color = XColors.Text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                    Text("Até quatro canais simultâneos. O som vem apenas da tela selecionada.",
                        color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("O limite depende das conexões do provedor e dos decodificadores do aparelho.",
                        color = XColors.Body, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    if (hasPlayers && !audioAllowed) Text("Som temporariamente indisponível enquanto outro aplicativo usa o áudio.",
                        color = XColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
            items((0 until 4).toList(), key = { it }) { index ->
                val channel = channels.firstOrNull { it.id == slotIds.getOrNull(index) }
                val focusModifier = Modifier.focusRequester(requests[index]).onFocusChanged {
                    if (it.isFocused) focusedTile = index else if (focusedTile == index) focusedTile = null
                }
                if (channel == null) EmptyTile(index, focusModifier) { picking = index }
                else MultiTile(index, channel, audible == index, audioAllowed, enabled, focusModifier,
                    onSelect = { onAudible(index) }, onRemove = { onSetSlot(index, null); pendingFocus = index },
                    onFullscreen = { onPlay(PlayerRequest(channel.name, channel.url, true, listOf(channel), 0, channel.fallbackUrl)) })
            }
        }
    }
    picking?.let { slot ->
        ChannelPicker(channels, onDismiss = { picking = null }) { channel ->
            onSetSlot(slot, channel.id); onAudible(slot); picking = null; pendingFocus = slot
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyTile(index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth().testTag("multi-tile-${index + 1}").aspectRatio(16f / 9f).focusGlow(shape, zoom = 1f).clip(shape)
        .background(XColors.Panel).border(1.dp, XColors.Line, shape)
        .combinedClickable(onClickLabel = "Adicionar canal ${index + 1}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.AddCircleOutline, "Adicionar canal ${index + 1}", tint = XColors.Accent, modifier = Modifier.size(30.dp))
        Text("Adicionar canal", color = XColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelPicker(channels: List<Channel>, onDismiss: () -> Unit, onChoose: (Channel) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(channels, query) { channels.filter { it.name.contains(query, true) } }
    Dialog(onDismissRequest = onDismiss) {
        // The dialog has its own window and text-input owner. Close its native IME before
        // removing the dialog, otherwise it can remain over the videos after selection.
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val closePicker = { keyboard?.hide(); focus.clearFocus(force = true); onDismiss() }
        GlassPanel(Modifier.widthIn(max = 500.dp).fillMaxWidth().heightIn(max = 560.dp), RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Escolher canal", color = XColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                PillField(query, { query = it }, "Buscar canal", Modifier.fillMaxWidth().padding(top = 14.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 390.dp).padding(top = 12.dp)) {
                    if (filtered.isEmpty()) item { Text("Nenhum canal encontrado.", color = XColors.TextDim, modifier = Modifier.padding(12.dp)) }
                    items(filtered, key = { it.id }) { channel ->
                        val shape = RoundedCornerShape(8.dp)
                        Row(Modifier.fillMaxWidth().testTag("choose-channel-${channel.id}").focusGlow(shape, zoom = 1f).clip(shape)
                            .combinedClickable { keyboard?.hide(); focus.clearFocus(force = true); onChoose(channel) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LiveTv, null, tint = XColors.TextDim, modifier = Modifier.size(20.dp))
                            Text("${channel.number}. ${channel.name}", color = XColors.Text, fontSize = 14.sp,
                                modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconAction(Icons.Filled.Close, "Fechar", onClick = closePicker)
                }
            }
        }
    }
}

@AndroidXOptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiTile(index: Int, channel: Channel, active: Boolean, audioAllowed: Boolean, enabled: Boolean, modifier: Modifier = Modifier,
                      onSelect: () -> Unit, onRemove: () -> Unit, onFullscreen: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth().testTag("multi-tile-${index + 1}").aspectRatio(16f / 9f).focusGlow(shape, zoom = 1f).clip(shape)
        .background(Color.Black).border(if (active) 2.dp else 1.dp, if (active) XColors.Accent else XColors.Line, shape)
        .combinedClickable(onClick = onSelect)) {
        if (enabled) MultiPlayer(index, channel, active && audioAllowed, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC0D0F12))) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, color = XColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconAction(if (active) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    if (active) "Som ativo na tela ${index + 1}" else "Ativar som da tela ${index + 1}", onClick = onSelect)
                IconAction(Icons.Filled.Fullscreen, "Abrir tela ${index + 1}", onClick = onFullscreen)
                IconAction(Icons.Filled.Close, "Remover tela ${index + 1}", onClick = onRemove)
            }
        }
    }
}

@AndroidXOptIn(UnstableApi::class)
@Composable
private fun MultiPlayer(index: Int, channel: Channel, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(channel.id, channel.url, channel.fallbackUrl) {
        buildPlayer(context, handleAudioFocus = false).apply {
            volume = 0f; setMediaItem(MediaItem.fromUri(channel.url)); prepare(); playWhenReady = true
        }
    }
    var state by remember(player) { mutableStateOf("buffering") }
    var fallbackTried by remember(player) { mutableStateOf(false) }
    DisposableEffect(player, channel.fallbackUrl) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) state = "playing" else if (state != "error") state = "paused" }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) state = "buffering"
                if (playbackState == Player.STATE_READY && player.isPlaying) state = "playing"
            }
            override fun onPlayerError(error: PlaybackException) {
                if (!fallbackTried && !channel.fallbackUrl.isNullOrBlank() && channel.fallbackUrl != channel.url) {
                    fallbackTried = true; player.setMediaItem(MediaItem.fromUri(channel.fallbackUrl)); player.prepare()
                    player.playWhenReady = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                } else state = "error"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    DisposableEffect(owner, player) {
        var resume = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { resume = player.playWhenReady; player.pause() }
                Lifecycle.Event.ON_START -> if (resume) { player.play(); resume = false }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, active) {
        player.volume = if (active) 1f else 0f
    }
    Box(modifier.testTag("multi-player-${index + 1}").semantics { stateDescription = state }) {
        AndroidView(factory = { c -> PlayerView(c).apply {
            useController = false; this.player = player
            isFocusable = false; isFocusableInTouchMode = false; descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } }, modifier = Modifier.fillMaxSize())
        if (state == "error") Text("Não foi possível reproduzir este canal.", color = XColors.Text, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Center).background(Color(0xCC0D0F12)).padding(8.dp))
    }
}

/** A single screen-level audio focus prevents the players from pausing one another. */
@Composable
private fun rememberMultiAudioFocus(wanted: Boolean): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val manager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var allowed by remember { mutableStateOf(false) }
    DisposableEffect(wanted, owner, manager) {
        if (!wanted) {
            allowed = false
            onDispose { }
        } else {
            val listener = AudioManager.OnAudioFocusChangeListener { change -> allowed = change == AudioManager.AUDIOFOCUS_GAIN }
            val request = if (Build.VERSION.SDK_INT >= 26) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AndroidAudioAttributes.Builder().setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                    .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setOnAudioFocusChangeListener(listener).build() else null
            var requested = false
            fun acquire() {
                if (requested) return
                @Suppress("DEPRECATION")
                val result = if (Build.VERSION.SDK_INT >= 26 && request != null) manager.requestAudioFocus(request)
                    else manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                requested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                allowed = requested
            }
            fun abandon() {
                if (requested) {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= 26 && request != null) manager.abandonAudioFocusRequest(request)
                    else manager.abandonAudioFocus(listener)
                }
                requested = false; allowed = false
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> acquire()
                    Lifecycle.Event.ON_STOP -> abandon()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) acquire()
            onDispose { owner.lifecycle.removeObserver(observer); abandon() }
        }
    }
    return allowed
}
