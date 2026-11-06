package com.xtream.player.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.xtream.player.ui.player.PlayerScreen

@Composable
fun XtreamApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun dismissKeyboard() { keyboard?.hide(); focusManager.clearFocus(force = true) }
    LaunchedEffect(Unit) {
        if (!vm.restoring && vm.session != null && nav.currentDestination?.route == "login") {
            nav.navigate("main") { popUpTo("login") { inclusive = true } }
        }
    }
    LaunchedEffect(vm.notice) { vm.notice?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.consumeNotice() } }
    fun openPlayer(request: PlayerRequest) {
        dismissKeyboard(); vm.startPlayback(request)
        nav.navigate("player") { launchSingleTop = true }
    }
    NavHost(nav, startDestination = "login") {
        composable("login") {
            LoginScreen(vm.loading || vm.restoring, vm.loginError) { server, user, pass ->
                vm.login(server, user, pass) {
                    dismissKeyboard()
                    nav.navigate("main") { popUpTo(nav.graph.startDestinationId) { inclusive = true }; launchSingleTop = true }
                }
            }
        }
        composable("main") {
            val actions = MainActions(
                onPlay = ::openPlayer,
                onOpenDetails = { item -> dismissKeyboard(); vm.openDetails(item) },
                onCloseDetails = vm::closeDetails,
                onPlayItem = { item -> vm.playItem(item, ::openPlayer) },
                onPlaySeries = { vm.playSeries(::openPlayer) },
                onPlayEpisode = { vm.playEpisode(it, ::openPlayer) },
                onToggleFav = vm::toggleFavorite,
                onRefreshEpg = vm::refreshEpg,
                onLogout = {
                    dismissKeyboard(); vm.logout()
                    nav.navigate("login") { popUpTo("main") { inclusive = true }; launchSingleTop = true }
                },
                onRefreshContent = vm::refreshContent,
                onChannelFocused = vm::channelFocused,
                onSaveCredentials = { server, user, pass -> vm.login(server, user, pass) { dismissKeyboard() } },
            )
            MainScreen(vm.session, vm.content, vm.epg, vm.favorites, vm.epgUpdatedAt, vm.epgLoading, vm.epgMessage,
                vm.loading, vm.loginError, vm.detailItem, vm.episodeSelection, vm.episodeLoading, vm.episodeError,
                actions, previewEnabled = entry?.destination?.route == "main", restoreChannelId = vm.lastPlayedChannelId,
                onChannelRestored = vm::consumeChannelRestore)
        }
        composable("player") {
            vm.playerRequest?.let { request -> PlayerScreen(request, onBack = { nav.popBackStack() }, onProgress = vm::saveProgress,
                onChannelChanged = vm::channelPlaying) }
        }
    }
}
