package com.xtream.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xtream.player.data.DemoData
import com.xtream.player.data.PlayerRequest
import com.xtream.player.ui.components.Tab
import com.xtream.player.ui.login.LoginScreen
import com.xtream.player.ui.main.MainActions
import com.xtream.player.ui.main.MainScreen
import com.xtream.player.ui.player.PlayerScreen
import com.xtream.player.ui.theme.XtreamTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rode: ./gradlew testDebugUnitTest  → PNGs em app/snapshots/ */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1376dp-h768dp-mdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val actions = MainActions({}, {}, {}, {}, {})

    private fun main(tab: Tab, name: String) {
        rule.setContent {
            XtreamTheme {
                MainScreen(DemoData.session(), DemoData.content(), DemoData.epg(), listOf("ch:c2"), System.currentTimeMillis(), false, null, actions, tab, previewEnabled = false)
            }
        }
        rule.onRoot().captureRoboImage("snapshots/$name.png")
    }

    @Test fun login() {
        rule.setContent { XtreamTheme { LoginScreen(false, null) { _, _, _ -> } } }
        rule.onRoot().captureRoboImage("snapshots/01_login.png")
    }
    @Test fun home() = main(Tab.Home, "02_home")
    @Test fun movies() = main(Tab.Movies, "03_filmes")
    @Test fun series() = main(Tab.Series, "04_series")
    @Test fun live() = main(Tab.Live, "05_tv_ao_vivo")
    @Test fun settings() = main(Tab.Settings, "06_configuracoes")
    @Test fun player() {
        rule.setContent { XtreamTheme { PlayerScreen(PlayerRequest("Aurora News", "http://x/y.m3u8", true, DemoData.channels, 0), {}, previewMode = true) } }
        rule.onRoot().captureRoboImage("snapshots/07_player.png")
    }
}
