package com.example.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.iptv.data.model.VideoDecoder

/**
 * Creates the ExoPlayer instance used by both the inline preview and the fullscreen player.
 *
 * Highlights:
 *  - HTTP data source with a real user agent and cross-protocol redirects (panels often
 *    redirect http -> https or to another port);
 *  - adaptive buffer, sized from the user setting;
 *  - hardware decoder preferred, with automatic software fallback for wide codec support;
 *  - MPEG-TS (`.ts`) and HLS (`.m3u8`) are both supported.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {

    const val USER_AGENT = "IPTVNative/2.0 (Android; ExoPlayer)"

    fun create(context: Context, decoder: VideoDecoder, bufferSeconds: Int): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context).apply {
            // Allows ExoPlayer to fall back to a software decoder when the hardware one fails.
            setEnableDecoderFallback(decoder != VideoDecoder.HARDWARE)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val minimumBufferMs = (bufferSeconds.coerceIn(5, 60)) * 1_000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minimumBufferMs, minimumBufferMs * 3, 1_000, 2_000)
            .build()

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    fun mediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        mimeTypeFor(url)?.let { builder.setMimeType(it) }
        return builder.build()
    }

    private fun mimeTypeFor(url: String): String? {
        val clean = url.substringBefore('?').lowercase()
        return when {
            clean.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            clean.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            clean.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            else -> null
        }
    }
}
