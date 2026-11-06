package com.example.iptv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

/**
 * Inline ExoPlayer surface used by the third column of the live TV screen.
 * It handles buffering, lifecycle pause/resume and one automatic retry with the
 * alternative stream format (MPEG-TS <-> HLS) before showing an error.
 */
@OptIn(UnstableApi::class)
@Composable
fun PreviewPlayer(
    request: PlaybackRequest?,
    decoder: VideoDecoder,
    bufferSeconds: Int,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {}
) {
    if (request == null) {
        Box(modifier = modifier.background(Color(0xFF0F1116)), contentAlignment = Alignment.Center) {
            placeholder()
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBuffering by remember(request.url) { mutableStateOf(true) }
    var errorMessage by remember(request.url) { mutableStateOf<String?>(null) }
    var triedFallback by remember(request.url) { mutableStateOf(false) }

    val player = remember(context, decoder, bufferSeconds) { PlayerFactory.create(context, decoder, bufferSeconds) }

    fun play(url: String) {
        errorMessage = null
        isBuffering = true
        player.setMediaItem(PlayerFactory.mediaItem(url))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player, request.url) {
        triedFallback = false
        play(request.url)
    }

    DisposableEffect(player, request.url) {
        val listener = object : Player.Listener {
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
                    play(fallback)
                } else {
                    isBuffering = false
                    errorMessage = error.message ?: "Não foi possível reproduzir este canal."
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
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

    Box(modifier = modifier.background(Color.Black)) {
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

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(34.dp),
                color = AccentCoral,
                strokeWidth = 3.dp
            )
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = AccentCoral,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    text = "Canal indisponível",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(320.dp)
                )
                TextButton(onClick = {
                    triedFallback = false
                    play(request.url)
                }) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = AccentCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tentar novamente",
                        color = AccentCoral,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
