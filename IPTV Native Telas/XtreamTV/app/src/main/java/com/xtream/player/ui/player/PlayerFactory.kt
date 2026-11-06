package com.xtream.player.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

@OptIn(UnstableApi::class)
fun buildPlayer(context: Context, trackSelector: DefaultTrackSelector? = null): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")   // muitos servidores IPTV exigem UA de player
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
    val b = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true),
    ).setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
        .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
    if (trackSelector != null) b.setTrackSelector(trackSelector)
    return b.build()
}
