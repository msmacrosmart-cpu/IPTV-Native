package com.example.iptv.player

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.iptv.data.model.PlaybackRequest
import com.example.iptv.data.model.VideoDecoder
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.LiveRed
import com.example.iptv.util.FormatUtils
import com.example.iptv.util.findActivity
import kotlinx.coroutines.delay

/**
 * Fullscreen Media3 ExoPlayer with IPTV controls.
 *
 * - locked to landscape while open (sensorLandscape in the manifest + explicit request here);
 * - immersive fullscreen with screen-on flag;
 * - automatic retry with the alternative stream format (MPEG-TS <-> HLS);
 * - channel zapping (previous/next) when opened from the live TV screen;
 * - seek bar for movies and series episodes.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    decoder: VideoDecoder,
    bufferSeconds: Int,
    onBack: () -> Unit,
    onNextChannel: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var errorMessage by remember(request.url) { mutableStateOf<String?>(null) }
    var triedFallback by remember(request.url) { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    val player = remember { PlayerFactory.create(context, decoder, bufferSeconds) }

    fun startPlayback(url: String) {
        errorMessage = null
        isBuffering = true
        player.setMediaItem(PlayerFactory.mediaItem(url))
        player.prepare()
        player.playWhenReady = true
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(request.url) {
        triedFallback = false
        startPlayback(request.url)
    }

    DisposableEffect(player, request.url) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        errorMessage = null
                    }

                    Player.STATE_ENDED -> isBuffering = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val fallback = request.fallbackUrl
                if (!triedFallback && !fallback.isNullOrBlank()) {
                    triedFallback = true
                    startPlayback(fallback)
                } else {
                    isBuffering = false
                    errorMessage = error.message ?: "Falha ao reproduzir este conteúdo."
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Position/duration polling (seek bar).
    LaunchedEffect(player) {
        while (true) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration
            }
            delay(500)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (errorMessage == null) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Immersive mode + landscape lock + keep screen on.
    DisposableEffect(activity) {
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (activity != null && previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    BackHandler { onBack() }

    val hasSeekBar = !request.isLive && durationMs > 0L

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = player }
        )

        // -------- top bar --------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC000000), Color(0x66000000), Color.Transparent)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, description = "Voltar", onClick = onBack)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                request.subtitle?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (request.isLive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LiveRed)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AO VIVO",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -------- buffering --------
        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(46.dp),
                color = AccentCoral,
                strokeWidth = 3.dp
            )
        }

        // -------- error --------
        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(460.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE610121A))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = AccentCoral,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Não foi possível reproduzir",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayerPillButton(
                        icon = Icons.Default.Refresh,
                        label = "Tentar novamente",
                        onClick = {
                            triedFallback = false
                            startPlayback(request.url)
                        }
                    )
                    if (!request.fallbackUrl.isNullOrBlank()) {
                        PlayerPillButton(
                            icon = Icons.Default.PlayArrow,
                            label = "Formato alternativo",
                            onClick = {
                                request.fallbackUrl?.let { fallback ->
                                    triedFallback = true
                                    startPlayback(fallback)
                                }
                            }
                        )
                    }
                }
            }
        }

        // -------- bottom controls --------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (request.isLive && onPreviousChannel != null) {
                    PlayerCircleButton(
                        icon = Icons.Default.SkipPrevious,
                        description = "Canal anterior",
                        onClick = onPreviousChannel
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }

                PlayerCircleButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    description = if (isPlaying) "Pausar" else "Reproduzir",
                    highlighted = true,
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                        } else {
                            if (player.playbackState == Player.STATE_IDLE) startPlayback(request.url) else player.play()
                        }
                    }
                )

                if (request.isLive && onNextChannel != null) {
                    Spacer(modifier = Modifier.width(14.dp))
                    PlayerCircleButton(
                        icon = Icons.Default.SkipNext,
                        description = "Próximo canal",
                        onClick = onNextChannel
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                PlayerCircleButton(
                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    description = if (isMuted) "Ativar som" else "Silenciar",
                    onClick = {
                        isMuted = !isMuted
                        player.volume = if (isMuted) 0f else 1f
                    }
                )
            }

            if (hasSeekBar) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FormatUtils.formatClock(if (isScrubbing) scrubValue.toLong() else positionMs),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Slider(
                        value = if (isScrubbing) scrubValue else positionMs.toFloat(),
                        onValueChange = { value ->
                            isScrubbing = true
                            scrubValue = value
                        },
                        onValueChangeFinished = {
                            player.seekTo(scrubValue.toLong())
                            isScrubbing = false
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCoral,
                            activeTrackColor = AccentCoral,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = FormatUtils.formatClock(durationMs),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val background = when {
        isFocused -> AccentCoral
        highlighted -> Color(0x66000000)
        else -> Color(0x80000000)
    }
    val border = when {
        isFocused -> Color.White
        highlighted -> AccentCoral
        else -> Color.White.copy(alpha = 0.25f)
    }

    Box(
        modifier = Modifier
            .size(if (highlighted) 60.dp else 44.dp)
            .scale(if (isFocused) 1.06f else 1f)
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, border, CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(if (highlighted) 30.dp else 22.dp)
        )
    }
}

@Composable
private fun PlayerPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) AccentCoral else Color(0x33FFFFFF))
            .border(1.dp, Color.White.copy(alpha = if (isFocused) 1f else 0.2f), RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
