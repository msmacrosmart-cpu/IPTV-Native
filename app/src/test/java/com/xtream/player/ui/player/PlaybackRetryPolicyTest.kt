package com.xtream.player.ui.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class PlaybackRetryPolicyTest {
    @Test fun retriesNetworkTimeoutHttpAndLiveWindowFailures() {
        assertTrue(retryPlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(retryPlaybackError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertTrue(retryPlaybackError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertTrue(retryPlaybackError(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW))
        assertFalse(retryPlaybackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertFalse(retryPlaybackError(PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED))
    }
    @Test fun retriesIndefinitelyWithBoundedBackoffRatherThanStoppingAfterThreeAttempts() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 15000L, 30000L), (0..5).map(::playbackRetryDelayMillis))
        assertEquals(30000L, playbackRetryDelayMillis(10000))
        assertEquals(30000L, playbackRetryDelayMillis(Int.MAX_VALUE))
    }
}
