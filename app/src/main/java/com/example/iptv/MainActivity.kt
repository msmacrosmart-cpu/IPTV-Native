package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iptv.data.repository.XtreamRepository
import com.example.iptv.player.PlayerScreen
import com.example.iptv.ui.components.AlertBanner
import com.example.iptv.ui.components.AppLogo
import com.example.iptv.ui.components.TopographicBackground
import com.example.iptv.ui.components.TvNavigationRail
import com.example.iptv.ui.screens.DashboardScreen
import com.example.iptv.ui.screens.LiveTvScreen
import com.example.iptv.ui.screens.LoginScreen
import com.example.iptv.ui.screens.SeriesScreen
import com.example.iptv.ui.screens.SettingsScreen
import com.example.iptv.ui.screens.VodScreen
import com.example.iptv.ui.theme.AccentCoral
import com.example.iptv.ui.theme.BackgroundDeep
import com.example.iptv.ui.theme.IPTVNativeTheme
import com.example.iptv.ui.theme.TextSecondary
import com.example.iptv.viewmodel.AuthViewModel
import com.example.iptv.viewmodel.MainTvViewModel
import com.example.iptv.viewmodel.SessionState
import com.example.iptv.viewmodel.TvNavSection

/**
 * Single-activity host. The session lives in [XtreamRepository] (owned by the Application), so
 * configuration changes and process restarts never lose the login.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository: XtreamRepository = (application as IPTVApplication).repository

        setContent {
            IPTVNativeTheme {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(repository))
                val mainViewModel: MainTvViewModel = viewModel(factory = MainTvViewModel.Factory(repository))
                IPTVNativeApp(authViewModel = authViewModel, mainViewModel = mainViewModel)
            }
        }
    }
}

@Composable
private fun IPTVNativeApp(
    authViewModel: AuthViewModel,
    mainViewModel: MainTvViewModel
) {
    val session by authViewModel.session.collectAsState()
    val warning by authViewModel.warningMessage.collectAsState()
    val alert by mainViewModel.alert.collectAsState()
    val settings by mainViewModel.settings.collectAsState()
    val fullscreen by mainViewModel.fullscreen.collectAsState()
    val section by mainViewModel.section.collectAsState()

    // Load the catalogue whenever a (new) account becomes available.
    val accountKey = (session as? SessionState.LoggedIn)?.account?.username
    LaunchedEffect(accountKey) {
        if (!accountKey.isNullOrBlank()) {
            mainViewModel.refreshAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        when (val state = session) {
            SessionState.Restoring -> SplashContent()

            SessionState.LoggedOut -> LoginScreen(viewModel = authViewModel)

            is SessionState.LoggedIn -> {
                val request = fullscreen
                if (request != null) {
                    var nextChannel: (() -> Unit)? = null
                    var previousChannel: (() -> Unit)? = null
                    if (request.isLive) {
                        nextChannel = { mainViewModel.zapChannel(forward = true) }
                        previousChannel = { mainViewModel.zapChannel(forward = false) }
                    }

                    PlayerScreen(
                        request = request,
                        decoder = settings.decoder,
                        bufferSeconds = settings.bufferSeconds,
                        onBack = { mainViewModel.closeFullscreen() },
                        onNextChannel = nextChannel,
                        onPreviousChannel = previousChannel
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    ) {
                        TvNavigationRail(
                            currentSection = section,
                            onSectionSelected = { mainViewModel.selectSection(it) }
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when (section) {
                                TvNavSection.HOME -> DashboardScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { mainViewModel.selectSection(it) }
                                )

                                TvNavSection.LIVE_TV -> LiveTvScreen(viewModel = mainViewModel)

                                TvNavSection.MOVIES -> VodScreen(viewModel = mainViewModel)

                                TvNavSection.SERIES -> SeriesScreen(viewModel = mainViewModel)

                                TvNavSection.SETTINGS -> SettingsScreen(
                                    viewModel = mainViewModel,
                                    onLogout = {
                                        mainViewModel.logout()
                                        authViewModel.logout()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        warning?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 10.dp)
            ) {
                AlertBanner(message = message, onDismiss = authViewModel::dismissWarning)
            }
        }

        alert?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 18.dp)
            ) {
                AlertBanner(message = message, onDismiss = mainViewModel::consumeAlert)
            }
        }
    }
}

@Composable
private fun SplashContent() {
    TopographicBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(markSize = 74)
            Spacer(modifier = Modifier.height(26.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = AccentCoral,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Validando sua sessão…",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
