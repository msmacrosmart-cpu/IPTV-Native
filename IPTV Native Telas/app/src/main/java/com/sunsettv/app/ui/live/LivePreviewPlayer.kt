package com.sunsettv.app.ui.live

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sunsettv.app.ui.theme.SunsetColors

private enum class PreviewState { Idle, Loading, Playing, Error }

/**
 * Janela de preview ao vivo (Media3 ExoPlayer + HLS). Troca de canal ao mudar [url].
 * O PlayerView NÃO recebe foco, então o D-pad nunca fica "preso" no vídeo.
 */
@Composable
fun LivePreviewPlayer(url: String?, modifier: Modifier = Modifier, muted: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(PreviewState.Idle) }
    val currentUrl by rememberUpdatedState(url)

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("SunsetTV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state = when (playbackState) {
                    Player.STATE_BUFFERING -> PreviewState.Loading
                    Player.STATE_READY -> PreviewState.Playing
                    else -> state
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                state = PreviewState.Error
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        if (url == null) {
            player.stop()
            player.clearMediaItems()
            state = PreviewState.Idle
        } else {
            state = PreviewState.Loading
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
    }

    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    // Pausa ao sair da tela / abrir o player em tela cheia; retoma ao voltar (ao vivo: vai para a borda).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (currentUrl != null) {
                    player.seekToDefaultPosition()
                    player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
        )
        when (state) {
            PreviewState.Loading -> CircularProgressIndicator(
                color = SunsetColors.Coral,
                modifier = Modifier.align(Alignment.Center),
            )
            PreviewState.Error -> Text(
                "Não foi possível reproduzir este canal",
                color = SunsetColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            PreviewState.Idle -> Text(
                "Selecione um canal",
                color = SunsetColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            PreviewState.Playing -> Unit
        }
    }
}
