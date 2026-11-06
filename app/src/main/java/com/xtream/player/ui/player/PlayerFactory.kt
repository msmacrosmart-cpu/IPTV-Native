package com.xtream.player.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.example.iptv.IPTVApplication
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.model.VideoDecoder
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

@OptIn(UnstableApi::class)
fun buildPlayer(context: Context, trackSelector: DefaultTrackSelector? = null, handleAudioFocus: Boolean = true): ExoPlayer {
    val settings = (context.applicationContext as? IPTVApplication)?.repository?.settings?.value ?: UserSettings()
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(settings.userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
    val codecSelector = MediaCodecSelector { mime, secure, tunneling ->
        val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
        when (settings.decoder) {
            VideoDecoder.AUTO -> codecs
            VideoDecoder.HARDWARE -> codecs.sortedByDescending { it.hardwareAccelerated }
            VideoDecoder.COMPATIBILITY -> codecs.sortedByDescending { it.softwareOnly }
        }
    }
    val bufferMs = settings.bufferSeconds.coerceIn(5, 60) * 1_000
    val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(
        bufferMs, bufferMs * 2, minOf(2_500, bufferMs), minOf(5_000, bufferMs)
    ).build()
    val selector = trackSelector ?: DefaultTrackSelector(context)
    selector.setParameters(selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabled))
    return ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setMediaCodecSelector(codecSelector)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true),
    ).setMediaSourceFactory(DefaultMediaSourceFactory(http))
        .setTrackSelector(selector).setLoadControl(loadControl)
        .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
        .setAudioAttributes(AudioAttributes.DEFAULT, handleAudioFocus)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
}
