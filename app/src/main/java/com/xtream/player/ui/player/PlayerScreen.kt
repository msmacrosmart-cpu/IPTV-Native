package com.xtream.player.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xtream.player.data.PlayerRequest
import com.xtream.player.data.Channel
import com.xtream.player.ui.components.FocusBrush
import com.xtream.player.ui.components.GlassPanel
import com.xtream.player.ui.components.focusGlow
import com.xtream.player.ui.theme.XColors
import com.xtream.player.util.fmtTime
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private enum class Dlg { Audio, Subs, Speed, Aspect, Sleep }
private enum class Aspect(val label: String) { Fit("Ajustar"), Fill("Preencher"), Zoom("Zoom"), R169("16:9"), R43("4:3") }
private data class Opt(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Player avançado (estilo VLC): velocidade, trilhas de áudio/legenda, proporção, A-B repeat, sleep timer,
 * bloqueio de tela, gestos (volume/brilho/duplo toque), estatísticas, D-pad completo e troca de canal.
 */
@AndroidXOptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlayerRequest, onBack: () -> Unit, previewMode: Boolean = false,
    onProgress: (PlayerRequest, Long, Long) -> Unit = { _, _, _ -> },
    onChannelChanged: (Channel) -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity
    var index by remember { mutableIntStateOf(request.index) }
    val current = request.playlist.getOrNull(index)
    val title = current?.name ?: request.title
    val url = current?.url ?: request.url
    val fallbackUrl = current?.fallbackUrl ?: request.fallbackUrl
    LaunchedEffect(current?.id) { current?.let(onChannelChanged) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val trackSelector = remember { DefaultTrackSelector(context) }
    val player = remember { buildPlayer(context, trackSelector).apply { playWhenReady = !previewMode } }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var triedFallback by remember(url) { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var position by remember { mutableLongStateOf(if (previewMode) 872_000L else 0L) }
    var duration by remember { mutableLongStateOf(if (previewMode) 3_600_000L else 0L) }
    var controls by remember { mutableStateOf(previewMode) }
    var touchStamp by remember { mutableLongStateOf(0L) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var aspect by remember { mutableStateOf(Aspect.Fit) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var osd by remember { mutableStateOf<String?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var pointA by remember { mutableStateOf<Long?>(null) }
    var pointB by remember { mutableStateOf<Long?>(null) }
    var sleepAt by remember { mutableStateOf<Long?>(null) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var statsText by remember { mutableStateOf("") }

    val touch = { touchStamp = System.currentTimeMillis() }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val latestProgress by rememberUpdatedState(onProgress)

    DisposableEffect(player, request) {
        onDispose {
            if (!previewMode && !request.isLive && player.duration > 0)
                latestProgress(request, player.currentPosition, player.duration)
            player.release()
        }
    }
    DisposableEffect(lifecycleOwner, player) {
        var resume = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { resume = player.playWhenReady; player.pause() }
                Lifecycle.Event.ON_START -> if (resume && !previewMode) { player.play(); resume = false }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(url) {
        error = null
        pointA = null; pointB = null
        val start = if (!request.isLive) request.resumePositionMs.coerceAtLeast(0L) else 0L
        player.setMediaItem(MediaItem.fromUri(url), start)
        if (!previewMode) { player.prepare(); player.playWhenReady = true }
    }

    DisposableEffect(player, url, fallbackUrl) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(s: Int) { buffering = s == Player.STATE_BUFFERING; if (s == Player.STATE_READY) error = null }
            override fun onPlayerError(e: PlaybackException) {
                if (!triedFallback && !fallbackUrl.isNullOrBlank() && fallbackUrl != url) {
                    triedFallback = true
                    val resume = if (request.isLive) 0L else player.currentPosition.coerceAtLeast(0)
                    val shouldPlay = player.playWhenReady && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    player.setMediaItem(MediaItem.fromUri(fallbackUrl), resume)
                    player.prepare(); player.playWhenReady = shouldPlay
                } else {
                    // Playback exceptions can contain credential-bearing URLs; show the code only.
                    error = e.errorCodeName
                    controls = false
                }
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(l); onDispose { player.removeListener(l) }
    }

    // Polling de posição, loop A-B e estatísticas
    if (!previewMode) LaunchedEffect(player) {
        var lastSaved = 0L
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            if (!request.isLive && duration > 0 && System.currentTimeMillis() - lastSaved >= 5_000) {
                latestProgress(request, position, duration)
                lastSaved = System.currentTimeMillis()
            }
            val a = pointA; val b = pointB
            if (a != null && b != null && position >= b) player.seekTo(a)
            val vf = player.videoFormat
            statsText = buildString {
                append("Vídeo: ${vf?.width ?: 0}x${vf?.height ?: 0} ${vf?.sampleMimeType?.substringAfter('/') ?: ""}")
                if ((vf?.bitrate ?: 0) > 0) append("  ${(vf!!.bitrate / 1000)} kbps")
                append("\nÁudio: ${player.audioFormat?.sampleMimeType?.substringAfter('/') ?: "-"} ${player.audioFormat?.channelCount ?: 0}ch")
                append("\nBuffer: ${player.bufferedPercentage}%  Velocidade: ${"%.2f".format(speed)}x")
            }
            delay(400)
        }
    }
    // Auto-ocultar controles
    LaunchedEffect(controls, touchStamp, isPlaying, dlg) {
        if (controls && isPlaying && dlg == null && !previewMode) { delay(4500); controls = false }
    }
    LaunchedEffect(osd) { if (osd != null) { delay(1300); osd = null } }
    LaunchedEffect(showUnlock) { if (showUnlock) { delay(2500); showUnlock = false } }
    LaunchedEffect(error) { if (error != null) { delay(80); runCatching { retryFocus.requestFocus() } } }
    LaunchedEffect(controls, locked) {
        if (previewMode) return@LaunchedEffect
        delay(60)
        runCatching { if (error != null) retryFocus.requestFocus() else if (controls && !locked) playFocus.requestFocus() else rootFocus.requestFocus() }
    }
    LaunchedEffect(sleepAt) {
        val t = sleepAt ?: return@LaunchedEffect
        delay((t - System.currentTimeMillis()).coerceAtLeast(0)); player.pause(); onBack()
    }

    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var bright by remember { mutableFloatStateOf(0.6f) }

    val isLive = request.isLive
    val hasPlaylist = request.playlist.size > 1
    fun seekBy(ms: Long) { if (player.isCurrentMediaItemSeekable) { player.seekTo((player.currentPosition + ms).coerceAtLeast(0)); osd = (if (ms > 0) "+" else "−") + "${kotlin.math.abs(ms) / 1000}s" } }
    fun changeChannel(delta: Int) {
        if (!hasPlaylist) return
        index = (index + delta + request.playlist.size) % request.playlist.size
        osd = request.playlist[index].name
    }
    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    BackHandler {
        when {
            locked -> showUnlock = true
            controls -> controls = false
            else -> onBack()
        }
    }

    Box(
        Modifier.fillMaxSize().testTag("player-root").semantics { stateDescription = when { error != null -> "error"; isPlaying -> "playing"; buffering -> "buffering"; else -> "paused" } }.background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (locked) {
                    if (showUnlock && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                        locked = false; showUnlock = false; controls = true
                    } else showUnlock = true
                    return@onPreviewKeyEvent true
                }
                if (error != null) return@onPreviewKeyEvent false
                if (controls || dlg != null) { touch(); return@onPreviewKeyEvent false }
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { controls = true; true }
                    Key.DirectionLeft -> { seekBy(-10_000); true }
                    Key.DirectionRight -> { seekBy(10_000); true }
                    Key.DirectionUp, Key.DirectionDown -> { controls = true; true }
                    Key.MediaPlayPause, Key.Spacebar -> { togglePlay(); true }
                    Key.ChannelUp, Key.PageUp -> { changeChannel(+1); true }
                    Key.ChannelDown, Key.PageDown -> { changeChannel(-1); true }
                    else -> false
                }
            }
            .focusRequester(rootFocus).focusable()
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (locked) showUnlock = true else { controls = !controls; touch() } },
                    onDoubleTap = { off -> if (!locked) seekBy(if (off.x < size.width / 2) -10_000 else 10_000) },
                )
            }
            .pointerInput(locked) {
                var right = true
                detectVerticalDragGestures(onDragStart = { right = it.x > size.width / 2 }) { change, dy ->
                    if (locked) return@detectVerticalDragGestures
                    change.consume()
                    if (right) {
                        vol = (vol - dy / size.height * maxVol * 1.6f).coerceIn(0f, maxVol.toFloat())
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, vol.roundToInt(), 0)
                        osd = "Volume ${(vol / maxVol * 100).roundToInt()}%"
                    } else {
                        bright = (bright - dy / size.height * 1.6f).coerceIn(0.03f, 1f)
                        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = bright } }
                        osd = "Brilho ${(bright * 100).roundToInt()}%"
                    }
                }
            },
    ) {
        // ---------- Vídeo
        val ratioMod = when (aspect) {
            Aspect.R169 -> Modifier.aspectRatio(16f / 9f).align(Alignment.Center)
            Aspect.R43 -> Modifier.aspectRatio(4f / 3f).align(Alignment.Center)
            else -> Modifier.fillMaxSize()
        }
        if (previewMode) {
            Box(ratioMod.background(Brush.linearGradient(listOf(Color(0xFF16304F), Color(0xFF0A1220)))))
        } else AndroidView(
            factory = { c -> PlayerView(c).apply { useController = false; keepScreenOn = true; this.player = player; isFocusable = false; isFocusableInTouchMode = false; descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) } },
            update = {
                it.resizeMode = when (aspect) {
                    Aspect.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    Aspect.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    Aspect.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = ratioMod,
        )
        if (buffering && !previewMode) CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = XColors.Accent)

        // ---------- OSD
        osd?.let {
            Text(it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 22.dp, vertical = 8.dp))
        }
        if (showStats) Text(statsText, color = Color(0xFFE0E0E0), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 96.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xAA000000)).padding(10.dp))
        error?.let {
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xDD111113)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Falha na reprodução", color = XColors.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(it, color = XColors.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp).padding(vertical = 8.dp))
                PBtn("Tentar novamente", Icons.Filled.Refresh, Modifier.focusRequester(retryFocus)) {
                    error = null; triedFallback = false
                    val resume = if (request.isLive) 0L else player.currentPosition.coerceAtLeast(0)
                    player.setMediaItem(MediaItem.fromUri(url), resume)
                    player.prepare(); player.playWhenReady = true
                }
            }
        }

        // ---------- Bloqueio
        if (locked && showUnlock) Box(Modifier.align(Alignment.CenterEnd).padding(32.dp)) {
            RoundBtn(Icons.Filled.LockOpen, 60, "Desbloquear") { locked = false; showUnlock = false; controls = true }
        }

        // ---------- Controles
        AnimatedVisibility(controls && !locked, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compactControls = maxWidth < 760.dp
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(230.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))

                // Title and tools wrap into separate rows; tools remain reachable by touch/D-pad on phones.
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = if (compactControls) 12.dp else 24.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RoundBtn(Icons.Filled.ArrowBack, 44, "Voltar") { onBack() }
                        Text(title, color = Color.White, fontSize = if (compactControls) 20.sp else 26.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp, end = 10.dp).weight(1f))
                        if (isLive) Text("AO VIVO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(XColors.Live.copy(alpha = .7f)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tool(Icons.Filled.Speed, "${"%.2g".format(speed)}x", speed != 1f) { dlg = Dlg.Speed }
                        Tool(Icons.Filled.AspectRatio, aspect.label, aspect != Aspect.Fit) { dlg = Dlg.Aspect }
                        Tool(Icons.Filled.Repeat, when { pointA != null && pointB != null -> "A-B ●"; pointA != null -> "A→"; else -> "A-B" }, pointA != null) {
                            when {
                                pointA == null -> { pointA = player.currentPosition; osd = "Ponto A definido" }
                                pointB == null -> { if (player.currentPosition > (pointA ?: 0L)) { pointB = player.currentPosition; osd = "Loop A-B ativo" } else osd = "O ponto B deve estar depois do ponto A" }
                                else -> { pointA = null; pointB = null; osd = "Loop A-B desligado" }
                            }
                        }
                        Tool(Icons.Filled.Bedtime, sleepAt?.let { "${((it - System.currentTimeMillis()) / 60000 + 1)}m" } ?: "Sleep", sleepAt != null) { dlg = Dlg.Sleep }
                        Tool(Icons.Filled.Info, "Info", showStats) { showStats = !showStats }
                        Tool(Icons.Filled.Lock, "Bloq.", false) { locked = true; controls = false; showUnlock = true }
                    }
                }

                // Base
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = if (compactControls) 12.dp else 28.dp, vertical = 18.dp)) {
                    Row(Modifier.then(if (compactControls) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.then(if (compactControls) Modifier else Modifier.weight(1f)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasPlaylist) {
                                PBtn("Previous Channel", Icons.Filled.SkipPrevious) { changeChannel(-1) }
                                PBtn("Next Channel", Icons.Filled.SkipNext) { changeChannel(+1) }
                            } else if (!isLive) {
                                PBtn("−10s", Icons.Filled.Replay10) { seekBy(-10_000) }
                                PBtn("+10s", Icons.Filled.Forward10) { seekBy(10_000) }
                            }
                        }
                        // Play/Pause central
                        val ring = CircleShape
                        Box(
                            Modifier.size(78.dp).focusRequester(playFocus).focusGlow(ring, 3.dp, 1.06f).clip(ring)
                                .background(Color(0x33000000)).border(3.dp, XColors.Accent.copy(alpha = 0.85f), ring)
                                .combinedClickableCompat { togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pausar" else "Reproduzir", tint = XColors.Accent, modifier = Modifier.size(40.dp)) }
                        Row(Modifier.then(if (compactControls) Modifier else Modifier.weight(1f)), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            PBtn("Audio Settings", Icons.Filled.VolumeUp) { dlg = Dlg.Audio }
                            PBtn("Subtitles", Icons.Filled.ClosedCaption) { dlg = Dlg.Subs }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(if (dragging != null && duration > 0) (dragging!! * duration).toLong() else position), color = Color(0xFFD0D0D5), fontSize = 16.sp)
                        if (duration > 0) {
                            Slider(
                                value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
                                onValueChange = { dragging = it; touch() },
                                onValueChangeFinished = { dragging?.let { player.seekTo((it * duration).toLong()) }; dragging = null },
                                colors = SliderDefaults.colors(thumbColor = XColors.Accent, activeTrackColor = XColors.Accent, inactiveTrackColor = Color(0x55FFFFFF)),
                                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                            )
                            Text("-" + fmtTime(duration - position), color = Color(0xFFD0D0D5), fontSize = 16.sp)
                        } else {
                            Spacer(Modifier.weight(1f))
                            Text("AO VIVO", color = XColors.Live, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ---------- Diálogos
        when (dlg) {
            Dlg.Speed -> OptionDialog("Velocidade", listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).map { s ->
                Opt("${"%.2f".format(s).trimEnd('0').trimEnd('.')}x", s == speed) { speed = s; player.setPlaybackSpeed(s); dlg = null }
            }) { dlg = null }
            Dlg.Aspect -> OptionDialog("Proporção da imagem", Aspect.values().map { a -> Opt(a.label, a == aspect) { aspect = a; dlg = null } }) { dlg = null }
            Dlg.Sleep -> OptionDialog("Sleep timer", listOf(0, 15, 30, 45, 60, 90, 120).map { m ->
                Opt(if (m == 0) "Desligado" else "$m minutos", m == sleepMinutes) { sleepMinutes = m; sleepAt = if (m == 0) null else System.currentTimeMillis() + m * 60_000L; dlg = null }
            }) { dlg = null }
            Dlg.Audio -> {
                val opts = trackOptions(tracks, C.TRACK_TYPE_AUDIO, "Faixa de áudio") { g, i ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build(); dlg = null
                }
                OptionDialog("Áudio", opts.ifEmpty { listOf(Opt("Nenhuma faixa alternativa", true) { dlg = null }) }) { dlg = null }
            }
            Dlg.Subs -> {
                val off = !player.trackSelectionParameters.disabledTrackTypes.let { C.TRACK_TYPE_TEXT !in it }
                val opts = listOf(Opt("Desativado", off) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); dlg = null
                }) + trackOptions(tracks, C.TRACK_TYPE_TEXT, "Legenda") { g, i ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build(); dlg = null
                }
                OptionDialog("Legendas", opts) { dlg = null }
            }
            null -> {}
        }
    }
}

private fun trackOptions(tracks: Tracks, type: Int, fallback: String, onPick: (Tracks.Group, Int) -> Unit): List<Opt> {
    val out = mutableListOf<Opt>()
    var n = 1
    tracks.groups.filter { it.type == type }.forEach { g ->
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val lang = f.language?.let { Locale(it).displayLanguage.replaceFirstChar { c -> c.uppercase() } }
            val label = listOfNotNull(f.label ?: lang ?: "$fallback $n", if (f.channelCount > 0 && type == C.TRACK_TYPE_AUDIO) "${f.channelCount}ch" else null).joinToString(" · ")
            out += Opt(label, g.isTrackSelected(i)) { onPick(g, i) }
            n++
        }
    }
    return out
}

@Composable
private fun OptionDialog(title: String, options: List<Opt>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(Modifier.width(380.dp).heightIn(max = 420.dp), RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(title, color = XColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                    itemsIndexed(options) { i, o ->
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                                .focusGlow(shape, zoom = 1.02f).clip(shape)
                                .background(if (o.selected) XColors.Selection else Color(0x14FFFFFF))
                                .combinedClickableCompat(o.onClick).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(o.label, color = XColors.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (o.selected) Icon(Icons.Filled.Check, null, tint = XColors.Accent)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

@Composable
private fun PBtn(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.focusGlow(shape).clip(shape).background(Color(0x66FFFFFF).copy(alpha = 0.16f))
            .combinedClickableCompat(onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFFE6E6EA), modifier = Modifier.size(22.dp))
        Text(text, color = Color(0xFFE6E6EA), fontSize = 17.sp, modifier = Modifier.padding(start = 10.dp), maxLines = 1)
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.focusGlow(shape, 2.dp, 1.05f).clip(shape)
            .background(if (active) XColors.Selection else Color(0x29FFFFFF))
            .combinedClickableCompat(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, size: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).focusGlow(CircleShape, 2.dp, 1.08f).clip(CircleShape).background(Color(0x44000000)).combinedClickableCompat(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp)) }
}
