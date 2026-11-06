package com.xtream.player.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtream.player.data.*
import com.xtream.player.ui.browse.BrowseScreen
import com.xtream.player.ui.components.*
import com.xtream.player.ui.home.HomeScreen
import com.xtream.player.ui.live.LiveScreen
import com.xtream.player.ui.settings.SettingsScreen
import com.xtream.player.ui.theme.XColors

class MainActions(
    val onPlay: (PlayerRequest) -> Unit,
    val onPlayItem: (VodItem) -> Unit,
    val onToggleFav: (String) -> Unit,
    val onRefreshEpg: () -> Unit,
    val onLogout: () -> Unit,
    val onRefreshContent: () -> Unit = {},
    val onChannelFocused: (Channel) -> Unit = {},
    val onAddAccount: () -> Unit = {},
)

@Composable
fun MainScreen(
    session: Session?,
    content: ContentState,
    epg: Map<String, List<Programme>>,
    favorites: List<String>,
    epgUpdatedAt: Long?,
    epgLoading: Boolean,
    epgMessage: String?,
    actions: MainActions,
    initialTab: Tab = Tab.Home,
    previewEnabled: Boolean = true,
    restoreChannelId: String? = null,
    onChannelRestored: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var scopeAll by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var pendingSearch by remember { mutableStateOf(false) }
    LaunchedEffect(tab, pendingSearch) {
        if (pendingSearch && tab != Tab.Settings) {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
            pendingSearch = false
        }
    }
    val focusSearch: () -> Unit = {
        if (tab == Tab.Settings) { tab = Tab.Home; pendingSearch = true }
        else { runCatching { searchFocus.requestFocus() }; keyboard?.show() }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Row(Modifier.fillMaxSize()) {
            Sidebar(tab, { tab = it; query = "" }, onSearch = focusSearch)
            Column(Modifier.weight(1f)) {
                if (tab != Tab.Settings) {
                    val showScope = tab == Tab.Movies || tab == Tab.Series
                    TopBar(
                        placeholder = when (tab) {
                            Tab.Movies -> "Search movies..."; Tab.Series -> "Search series..."
                            Tab.Live -> "Search channels..."; else -> "Search in this playlist..."
                        },
                        query = query, onQuery = { query = it },
                        showScope = showScope, scopeAll = scopeAll, onScope = { scopeAll = it },
                        playlistName = session?.let { if (it.isDemo) "release-demo" else it.username } ?: "release-demo",
                        playlistInfo = "Xtream · ${content.channels.size} channels",
                        searchFocus = searchFocus,
                        onDownload = actions.onRefreshContent, onAdd = actions.onAddAccount, onKeyboard = focusSearch,
                    )
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.Home -> HomeScreen(content, epg, query, favorites, actions.onPlayItem,
                            { ch -> actions.onPlay(PlayerRequest(ch.name, ch.url, true, content.channels, content.channels.indexOf(ch))) },
                            actions.onToggleFav)
                        Tab.Movies -> BrowseScreen("Movies", "All Movies", content.movieCategories, content.movies, query, scopeAll, actions.onToggleFav, actions.onPlayItem)
                        Tab.Series -> BrowseScreen("Series", "All Series", content.seriesCategories, content.series, query, scopeAll, actions.onToggleFav, actions.onPlayItem)
                        Tab.Live -> LiveScreen(content, epg, favorites, query, actions.onToggleFav, actions.onPlay,
                            previewEnabled, actions.onChannelFocused, restoreChannelId, onChannelRestored)
                        Tab.Favorites -> FavoritesScreen(content, favorites, query, actions)
                        Tab.Settings -> SettingsScreen(session, content, epg, epgUpdatedAt, epgLoading, epgMessage, actions.onRefreshEpg, actions.onLogout, actions.onChannelFocused)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(content: ContentState, favorites: List<String>, query: String, actions: MainActions) {
    val vod = (content.movies + content.series).filter { it.favoriteKey in favorites && it.title.contains(query, true) }
    val chans = content.channels.filter { "ch:${it.id}" in favorites && it.name.contains(query, true) }
    LazyColumn(contentPadding = PaddingValues(26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("FAVORITOS", color = XColors.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("Segure OK / pressione e segure em um item para favoritar", color = XColors.TextDim, fontSize = 17.sp)
        }
        if (vod.isEmpty() && chans.isEmpty()) item { Text("Nenhum favorito ainda.", color = XColors.TextDim, fontSize = 18.sp) }
        if (chans.isNotEmpty()) item {
            SectionTitle("Canais")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(chans, key = { it.id }) { ch ->
                    CategoryChip(ch.name, false, onLongClick = { actions.onToggleFav("ch:${ch.id}") }) { actions.onPlay(PlayerRequest(ch.name, ch.url, true, chans, chans.indexOf(ch))) }
                }
            }
        }
        if (vod.isNotEmpty()) item {
            SectionTitle("Filmes e séries")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(vod, key = { it.favoriteKey }) { PosterCard(it, onLongClick = { actions.onToggleFav(it.favoriteKey) }) { actions.onPlayItem(it) } }
            }
        }
    }
}
