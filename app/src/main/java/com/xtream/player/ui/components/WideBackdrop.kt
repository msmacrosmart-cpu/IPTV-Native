package com.xtream.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage

/** A panel may label a portrait poster as a backdrop. Never render it as a wide banner. */
@Composable
fun WideBackdrop(url: String?, tag: String, loadedTag: String, rejectedTag: String, modifier: Modifier = Modifier) {
    var status by remember(url) { mutableStateOf(0) } // 0 loading, 1 landscape, -1 invalid/error
    if (!url.isNullOrBlank() && status >= 0) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.alpha(if (status == 1) 1f else 0f).testTag(
                when (status) { 1 -> loadedTag; -1 -> rejectedTag; else -> tag }
            ),
            onSuccess = { state ->
                val drawable = state.result.drawable
                status = if (drawable.intrinsicWidth > 0 && drawable.intrinsicWidth > drawable.intrinsicHeight &&
                    drawable.intrinsicHeight > 0) 1 else -1
            },
            onError = { status = -1 },
        )
    } else if (status == -1) {
        // Keep a diagnostic semantics marker, but no portrait artwork is drawn.
        Box(modifier.testTag(rejectedTag))
    }
}
