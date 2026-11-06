package com.xtream.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.iptv.R

/** The same user-provided image behind login and the app; the scrim preserves text contrast. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Preserve the supplied 3780×1890 source rather than enlarging a sampled thumbnail.
        // Coil shares one decoded bitmap across destinations and bounds the memory cache.
        val context = LocalContext.current
        val wallpaper = remember(context) {
            ImageRequest.Builder(context).data(R.drawable.app_background).size(Size.ORIGINAL).build()
        }
        Box(Modifier.fillMaxSize().testTag("app-background")) {
            AsyncImage(
                model = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (loaded) Modifier.testTag("app-background-loaded") else Modifier),
                onSuccess = { loaded = true },
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)))
        content()
    }
}
