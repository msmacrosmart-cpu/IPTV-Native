package com.xtream.player.ui.components

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import com.example.iptv.R

/** Original lossless wallpaper, including the contact/QR area: never crop, tint or blur it. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val wallpaper = ImageBitmap.imageResource(R.drawable.app_background)
    val isTv = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    // Mobile fills ultrawide landscape screens without cropping the artwork/contact.
    // TV keeps its existing aspect-preserving presentation.
    val scale = if (isTv) ContentScale.Fit else ContentScale.FillBounds
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().testTag("app-background")) {
            Image(wallpaper, contentDescription = null, contentScale = scale,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().testTag("app-background-loaded"))
        }
        // Contrast belongs to individual glass cards, not a scrim over the contact QR.
        content()
    }
}
