package com.xtream.player.ui.player

import androidx.media3.common.PlaybackException

/** Retry temporary source/network failures indefinitely, never hammer the panel in a tight loop. */
fun retryPlaybackError(code: Int): Boolean = code in setOf(
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
)

fun playbackRetryDelayMillis(attempt: Int): Long = when (attempt.coerceAtLeast(0)) {
    0 -> 1_000L
    1 -> 2_000L
    2 -> 4_000L
    3 -> 8_000L
    4 -> 15_000L
    else -> 30_000L
}
