package com.xtream.player.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xtream.player.data.AppViewModel
import com.xtream.player.data.PlayerRequest
import com.xtream.player.ui.login.LoginScreen
import com.xtream.player.ui.main.MainActions
import com.xtream.player.ui.main.MainScreen
import com.xtream.player.ui.player.EpisodeDialog
import com.xtream.player.ui.player.PlayerScreen

@Composable
fun XtreamApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!vm.restoring && vm.session != null && nav.currentDestination?.route == "login") {
            nav.navigate("main") { popUpTo("login") { inclusive = true } }
        }
    }
    LaunchedEffect(vm.notice) {
        vm.notice?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.consumeNotice() }
    }
    fun openPlayer(request: PlayerRequest) {
        vm.playerRequest = request
        nav.navigate("player") { launchSingleTop = true }
    }
    NavHost(nav, startDestination = "login") {
        composable("login") {
            LoginScreen(vm.loading || vm.restoring, vm.loginError) { server, user, pass ->
                vm.login(server, user, pass) {
                    nav.navigate("main") {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
        composable("main") {
            val actions = MainActions(
                onPlay = ::openPlayer,
                onPlayItem = { item -> vm.playItem(item, ::openPlayer) },
                onToggleFav = vm::toggleFavorite,
                onRefreshEpg = vm::refreshEpg,
                onLogout = {
                    vm.logout()
                    nav.navigate("login") { popUpTo("main") { inclusive = true }; launchSingleTop = true }
                },
                onRefreshContent = vm::refreshContent,
                onChannelFocused = vm::channelFocused,
                onAddAccount = { nav.navigate("login") { launchSingleTop = true } },
            )
            MainScreen(vm.session, vm.content, vm.epg, vm.favorites, vm.epgUpdatedAt,
                vm.epgLoading, vm.epgMessage, actions, previewEnabled = entry?.destination?.route == "main")
            vm.episodeSelection?.let { selection ->
                EpisodeDialog(selection, onDismiss = vm::dismissEpisodes) { vm.playEpisode(it, ::openPlayer) }
            }
        }
        composable("player") {
            vm.playerRequest?.let { request ->
                PlayerScreen(request, onBack = { nav.popBackStack() }, onProgress = vm::saveProgress)
            }
        }
    }
}
