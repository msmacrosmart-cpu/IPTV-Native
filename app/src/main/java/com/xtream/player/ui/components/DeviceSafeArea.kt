package com.xtream.player.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Keep controls away from overscan on TVs, projectors and HDMI sticks, but not phones. */
@Composable
fun DeviceSafeArea(content: @Composable BoxScope.() -> Unit) {
    val isTv = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = if (isTv) (maxWidth * .035f).coerceAtMost(64.dp) else 0.dp
        val vertical = if (isTv) (maxHeight * .035f).coerceAtMost(36.dp) else 0.dp
        Box(Modifier.fillMaxSize().padding(horizontal = horizontal, vertical = vertical).testTag("device-safe-content"), content = content)
    }
}
