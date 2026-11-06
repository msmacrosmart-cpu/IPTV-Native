package com.xtream.player.ui

import android.widget.Toast
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.example.iptv.data.model.PlaybackApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.iptv.data.tmdb.youtubeVideoId
import com.xtream.player.data.AppViewModel
import com.xtream.player.data.PlayerRequest
import com.xtream.player.ui.components.AppBackground
import com.xtream.player.ui.components.DeviceSafeArea
import com.xtream.player.ui.components.rememberHideKeyboard
import com.xtream.player.ui.login.LoginScreen
import com.xtream.player.ui.main.MainActions
import com.xtream.player.ui.main.MainScreen
import com.xtream.player.ui.player.PlayerScreen
import com.xtream.player.ui.settings.SettingsActions
import com.xtream.player.ui.splash.LoadingScreen
import kotlinx.coroutines.delay

@Composable
fun XtreamApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val context = LocalContext.current
    val hideKeyboard = rememberHideKeyboard()
    val focusManager = LocalFocusManager.current
    fun dismissKeyboard() { hideKeyboard(); focusManager.clearFocus(force = true) }
    LaunchedEffect(vm.notice) { vm.notice?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.consumeNotice() } }
    // Some TV IMEs deliver a pending show request after the login screen disappears.
    // Repeat the hide after navigation settles; never reopen or replace the system keyboard.
    LaunchedEffect(entry?.destination?.route) {
        if (entry?.destination?.route == "main") { delay(200); hideKeyboard() }
    }
    fun openPlayer(request: PlayerRequest) {
        vm.cancelHeroPlayback(); dismissKeyboard()
        val choice = when {
            request.isLive -> vm.settings.livePlayer
            request.episodeId != null -> vm.settings.seriesPlayer
            else -> vm.settings.moviePlayer
        }
        if (choice == PlaybackApp.EXTERNAL) {
            // Xtream URLs can contain credentials; the settings screen warns before enabling this.
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(request.url), "video/*")
            try { context.startActivity(Intent.createChooser(intent, "Abrir vídeo com")) }
            catch (_: ActivityNotFoundException) { Toast.makeText(context, "Nenhum aplicativo de vídeo encontrado.", Toast.LENGTH_LONG).show() }
        } else {
            vm.startPlayback(request)
            nav.navigate("player") { launchSingleTop = true }
        }
    }
    if (vm.restoring) {
        LoadingScreen(vm.loadingMessage)
        return
    }
    // Capture the destination only after the disk restore; subsequent session changes
    // are explicit navigation events, not a rebuilt NavHost graph.
    val initialDestination = remember { if (vm.session != null) "main" else "login" }
    AppBackground {
      Box(Modifier.fillMaxSize()) {
      NavHost(nav, startDestination = initialDestination) {
        composable("login") {
            DeviceSafeArea {
                LoginScreen(vm.loading || vm.restoring, vm.loginError, profiles = vm.profiles, onSwitchProfile = { id ->
                    dismissKeyboard()
                    vm.switchProfile(id) {
                        nav.navigate("main") { popUpTo("login") { inclusive = true }; launchSingleTop = true }
                    }
                }) { server, user, pass ->
                    vm.login(server, user, pass) {
                        dismissKeyboard()
                        nav.navigate("main") { popUpTo(nav.graph.startDestinationId) { inclusive = true }; launchSingleTop = true }
                    }
                }
            }
        }
        composable("main") {
            val actions = MainActions(
                onPlay = ::openPlayer,
                onOpenDetails = { item -> vm.cancelHeroPlayback(); dismissKeyboard(); vm.openDetails(item) },
                onCloseDetails = vm::closeDetails,
                onPlayItem = { item -> vm.playItem(item, ::openPlayer) },
                onPlayHero = { item -> vm.playHero(item, ::openPlayer) },
                onPrepareHeroes = vm::prepareHeroItems,
                onCancelHeroPlayback = vm::cancelHeroPlayback,
                onPlaySeries = { vm.playSeries(::openPlayer) },
                onPlayEpisode = { vm.playEpisode(it, ::openPlayer) },
                onTrailer = { rawId ->
                    youtubeVideoId(rawId)?.let { id ->
                        val url = Uri.Builder().scheme("https").authority("www.youtube.com").appendPath("watch")
                            .appendQueryParameter("v", id).build()
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(context, "Instale um aplicativo compatível com YouTube para abrir o trailer.", Toast.LENGTH_LONG).show() }
                        catch (_: SecurityException) { Toast.makeText(context, "Não foi possível abrir o trailer neste dispositivo.", Toast.LENGTH_LONG).show() }
                    }
                },
                onToggleFav = vm::toggleFavorite,
                onRefreshContent = vm::refreshContent,
                onChannelFocused = vm::channelFocused,
                settings = SettingsActions(
                    saveCredentials = { server, user, pass -> vm.login(server, user, pass) { dismissKeyboard() } },
                    switchProfile = { id -> dismissKeyboard(); vm.switchProfile(id) },
                    removeProfile = vm::removeProfile,
                    refreshAccount = vm::refreshAccount,
                    testConnection = vm::testConnection,
                    refreshEpg = vm::refreshEpg,
                    refreshContent = vm::refreshContent,
                    updateSettings = vm::updateUserSettings,
                    clearCache = vm::clearCache,
                    setPin = vm::setParentalPin,
                    unlock = vm::unlockParental,
                    lock = vm::lockParental,
                    clearPin = vm::clearParental,
                    toggleCategory = vm::toggleLockedCategory,
                    logout = {
                        dismissKeyboard(); vm.logout()
                        nav.navigate("login") { popUpTo("main") { inclusive = true }; launchSingleTop = true }
                    },
                ),
            )
            DeviceSafeArea {
                MainScreen(vm.session, vm.content, vm.epg, vm.favorites, vm.epgUpdatedAt, vm.epgLoading, vm.epgMessage,
                    vm.heroDetails, vm.loadingHeroKey, vm.loading, vm.loginError, vm.detailItem, vm.episodeSelection, vm.episodeLoading, vm.episodeError,
                    vm.detailExtras, vm.detailMetadataLoading, vm.detailMetadataMessage, vm.profiles, vm.settings, vm.parentalUnlocked, vm.parentalBusy, vm.testingConnection, vm.connectionTest, actions, previewEnabled = entry?.destination?.route == "main", restoreChannelId = vm.lastPlayedChannelId,
                    onChannelRestored = vm::consumeChannelRestore)
            }
        }
        composable("player") {
            vm.playerRequest?.let { request ->
                key(request.episodeId, request.url) {
                    PlayerScreen(request, onBack = { nav.popBackStack() }, onProgress = vm::saveProgress,
                        onChannelChanged = vm::channelPlaying, onEnded = vm::nextEpisode)
                }
            }
        }
      }
      if (vm.loading) LoadingScreen(vm.loadingMessage)
      }
    }
}
