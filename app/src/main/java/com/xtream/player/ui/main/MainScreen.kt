package com.xtream.player.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.example.iptv.data.model.VodDetails
import com.xtream.player.data.*
import com.xtream.player.ui.browse.BrowseScreen
import com.xtream.player.ui.components.*
import com.xtream.player.ui.detail.DetailScreen
import com.xtream.player.ui.home.HomeScreen
import com.xtream.player.ui.live.LiveScreen
import com.xtream.player.ui.multi.MultiScreen
import com.xtream.player.ui.settings.SettingsScreen
import com.xtream.player.ui.theme.XColors

class MainActions(
    val onPlay: (PlayerRequest) -> Unit,
    val onOpenDetails: (VodItem) -> Unit,
    val onCloseDetails: () -> Unit,
    val onPlayItem: (VodItem) -> Unit,
    val onPlayHero: (VodItem) -> Unit,
    val onPrepareHeroes: (List<VodItem>) -> Unit,
    val onCancelHeroPlayback: () -> Unit,
    val onPlaySeries: () -> Unit,
    val onPlayEpisode: (AppViewModel.EpisodeChoice) -> Unit,
    val onToggleFav: (String) -> Unit,
    val onRefreshEpg: () -> Unit,
    val onLogout: () -> Unit,
    val onRefreshContent: () -> Unit,
    val onChannelFocused: (Channel) -> Unit,
    val onSaveCredentials: (String, String, String) -> Unit,
)

@Composable
fun MainScreen(session: Session?, content: ContentState, epg: Map<String, List<Programme>>,
               favorites: List<String>, epgUpdatedAt: Long?, epgLoading: Boolean, epgMessage: String?,
               heroDetails: Map<String, VodDetails>, loadingHeroKey: String?,
               loading: Boolean, loginError: String?, detail: VodItem?, episodes: AppViewModel.EpisodeSelection?,
               episodeLoading: Boolean, episodeError: String?, actions: MainActions,
               previewEnabled: Boolean = true, restoreChannelId: String? = null, onChannelRestored: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var query by rememberSaveable { mutableStateOf("") }
    var slots by rememberSaveable { mutableStateOf(List(4) { "" }) }
    var audible by rememberSaveable { mutableIntStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(session?.server, session?.username) { slots = List(4) { "" }; audible = 0 }
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val selectTab: (Tab) -> Unit = { selected ->
        keyboard?.hide()
        if (inputMode.inputMode == InputMode.Touch) focusManager.clearFocus(force = true)
        actions.onCancelHeroPlayback()
        actions.onCloseDetails()
        tab = selected; query = ""
    }
    BackHandler(enabled = detail == null && tab != Tab.Home) { selectTab(Tab.Home) }

    @Composable fun Header(compact: Boolean) {
        TopBar(tab, query, { query = it }, session?.username ?: "Xtream",
            "Xtream · ${content.channels.size} canais", searchFocus, compact, actions.onRefreshContent)
    }
    @Composable fun Content(modifier: Modifier) {
        Box(modifier) {
            if (detail != null) DetailScreen(detail, detail.favoriteKey in favorites, episodes, episodeLoading, episodeError,
                onBack = actions.onCloseDetails, onFavorite = { actions.onToggleFav(detail.favoriteKey) },
                onPlay = { if (detail.isSeries) actions.onPlaySeries() else actions.onPlayItem(detail) }, onEpisode = actions.onPlayEpisode)
            else when (tab) {
                Tab.Home -> HomeScreen(content, epg, query, favorites, heroDetails, loadingHeroKey,
                    actions.onOpenDetails, actions.onPlayHero, actions.onPrepareHeroes,
                    { ch -> actions.onPlay(PlayerRequest(ch.name, ch.url, true, content.channels, content.channels.indexOf(ch), ch.fallbackUrl)) }, actions.onToggleFav)
                Tab.Live -> LiveScreen(content, epg, favorites, query, actions.onToggleFav, actions.onPlay,
                    previewEnabled, actions.onChannelFocused, restoreChannelId, onChannelRestored)
                Tab.Movies -> BrowseScreen("filmes", content.movieCategories, content.movies, query, favorites, actions.onToggleFav, actions.onOpenDetails)
                Tab.Series -> BrowseScreen("séries", content.seriesCategories, content.series, query, favorites, actions.onToggleFav, actions.onOpenDetails)
                Tab.Multi -> MultiScreen(content.channels, slots, audible, previewEnabled,
                    onSetSlot = { index, id -> slots = slots.toMutableList().also { it[index] = id.orEmpty() } },
                    onAudible = { audible = it }, onPlay = actions.onPlay)
                Tab.Settings -> SettingsScreen(session, content, epg, epgUpdatedAt, epgLoading, epgMessage,
                    loading, loginError, actions.onSaveCredentials, actions.onRefreshEpg, actions.onRefreshContent, actions.onLogout)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeader = maxWidth < 850.dp
        Row(Modifier.fillMaxSize()) {
            Sidebar(tab, selectTab, compact = false)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Header(compactHeader)
                Content(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
