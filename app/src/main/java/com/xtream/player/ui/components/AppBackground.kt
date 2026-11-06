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
import coil.compose.AsyncImage
import com.example.iptv.R

/** The same user-provided image behind login and the app; the scrim preserves text contrast. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Coil decodes at the viewport size instead of retaining a full 3780px bitmap on
        // low-memory phones and TV sticks. The packaged image itself is the supplied original.
        Box(Modifier.fillMaxSize().testTag("app-background")) {
            AsyncImage(
                model = R.drawable.app_background,
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
