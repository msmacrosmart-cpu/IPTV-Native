package com.xtream.player.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xtream.player.data.AppViewModel
import com.xtream.player.ui.login.LoginScreen
import com.xtream.player.ui.main.MainActions
import com.xtream.player.ui.main.MainScreen
import com.xtream.player.ui.player.PlayerScreen

@Composable
fun XtreamApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "login") {
        composable("login") {
            LoginScreen(vm.loading, vm.loginError) { s, u, p ->
                vm.login(s, u, p) { nav.navigate("main") { popUpTo("login") { inclusive = true } } }
            }
        }
        composable("main") {
            val actions = MainActions(
                onPlay = { vm.playerRequest = it; nav.navigate("player") },
                onPlayItem = { item -> vm.playItem(item) { vm.playerRequest = it; nav.navigate("player") } },
                onToggleFav = vm::toggleFavorite,
                onRefreshEpg = vm::refreshEpg,
                onLogout = { vm.logout(); nav.navigate("login") { popUpTo("main") { inclusive = true } } },
            )
            MainScreen(vm.session, vm.content, vm.epg, vm.favorites, vm.epgUpdatedAt, vm.epgLoading, vm.epgMessage, actions)
        }
        composable("player") {
            vm.playerRequest?.let { PlayerScreen(it, onBack = { nav.popBackStack() }) }
        }
    }
}
