package com.xtream.player.ui.components

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Close the platform IME, including TV keyboards that do not honor the Compose request alone. */
@Composable
fun rememberHideKeyboard(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val view = LocalView.current
    return remember(keyboard, context, view) {
        {
            keyboard?.hide()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
            Unit
        }
    }
}
