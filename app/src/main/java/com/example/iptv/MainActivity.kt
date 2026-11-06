package com.example.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.iptv.data.repository.XtreamRepository
import com.example.iptv.player.PlayerScreen
import com.example.iptv.ui.components.TvNavigationRail
import com.example.iptv.ui.screens.*
import com.example.iptv.ui.theme.IPTVNativeTheme
import com.example.iptv.viewmodel.AuthViewModel
import com.example.iptv.viewmodel.MainTvViewModel
import com.example.iptv.viewmodel.TvNavSection

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = XtreamRepository(applicationContext)
        val authViewModel = AuthViewModel(repository)
        val mainViewModel = MainTvViewModel(repository)

        setContent {
            IPTVNativeTheme {
                var isLoggedIn by remember {
                    mutableStateOf(repository.currentUser.isNotEmpty() && repository.currentServer.isNotEmpty())
                }

                val currentSection by mainViewModel.currentSection.collectAsState()
                val fullScreenUrl by mainViewModel.fullScreenUrl.collectAsState()
                val fullScreenTitle by mainViewModel.fullScreenTitle.collectAsState()

                if (!isLoggedIn) {
                    LoginScreen(
                        viewModel = authViewModel,
                        onLoginSuccess = {
                            isLoggedIn = true
                            mainViewModel.loadAllData()
                        }
                    )
                } else if (fullScreenUrl != null) {
                    PlayerScreen(
                        streamUrl = fullScreenUrl!!,
                        title = fullScreenTitle ?: "Transmissão ao Vivo",
                        onBack = { mainViewModel.closeFullScreen() }
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Barra de Navegação Lateral Android TV
                        TvNavigationRail(
                            currentSection = currentSection,
                            onSectionSelected = { mainViewModel.currentSection.value = it }
                        )

                        // Área de Conteúdo Ativo
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            when (currentSection) {
                                TvNavSection.HOME -> DashboardScreen(
                                    viewModel = mainViewModel,
                                    onOpenHeroDetails = { mainViewModel.currentSection.value = TvNavSection.SERIES },
                                    onPlayContent = { url, title -> mainViewModel.openFullScreen(url, title) }
                                )
                                TvNavSection.LIVE_TV -> LiveTvScreen(
                                    viewModel = mainViewModel,
                                    onFullScreen = { url, title -> mainViewModel.openFullScreen(url, title) }
                                )
                                TvNavSection.MOVIES -> VodScreen(
                                    viewModel = mainViewModel,
                                    onPlayMovie = { url, title -> mainViewModel.openFullScreen(url, title) }
                                )
                                TvNavSection.SERIES -> SeriesScreen(
                                    viewModel = mainViewModel,
                                    onPlaySeries = { url, title -> mainViewModel.openFullScreen(url, title) }
                                )
                                TvNavSection.FAVORITES -> DashboardScreen(
                                    viewModel = mainViewModel,
                                    onOpenHeroDetails = { mainViewModel.currentSection.value = TvNavSection.MOVIES },
                                    onPlayContent = { url, title -> mainViewModel.openFullScreen(url, title) }
                                )
                                TvNavSection.SETTINGS -> SettingsScreen(
                                    viewModel = mainViewModel,
                                    onLogout = { isLoggedIn = false }
                                )
                                TvNavSection.SEARCH -> LiveTvScreen(
                                    viewModel = mainViewModel,
                                    onFullScreen = { url, title -> mainViewModel.openFullScreen(url, title) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
