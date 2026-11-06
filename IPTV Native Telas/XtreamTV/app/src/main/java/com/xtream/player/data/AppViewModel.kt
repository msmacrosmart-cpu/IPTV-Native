package com.xtream.player.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel : ViewModel() {
    var session by mutableStateOf<Session?>(null); private set
    var content by mutableStateOf(DemoData.content()); private set
    var epg by mutableStateOf<Map<String, List<Programme>>>(emptyMap()); private set
    var epgUpdatedAt by mutableStateOf<Long?>(null); private set
    var epgLoading by mutableStateOf(false); private set
    var epgMessage by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    var playerRequest: PlayerRequest? = null
    val favorites = mutableStateListOf<String>()

    /** Use o servidor "demo" para entrar com dados de demonstração. */
    fun login(server: String, user: String, pass: String, onSuccess: () -> Unit) {
        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            loginError = "Preencha servidor, usuário e senha"; return
        }
        viewModelScope.launch {
            loading = true; loginError = null
            try {
                if (server.trim().equals("demo", true)) {
                    session = DemoData.session(); content = DemoData.content()
                    epg = DemoData.epg(); epgUpdatedAt = System.currentTimeMillis()
                } else {
                    val s = withContext(Dispatchers.IO) { XtreamApi.authenticate(server, user, pass) }
                    val c = withContext(Dispatchers.IO) { XtreamApi.loadContent(s) }
                    session = s; content = c; epg = emptyMap(); epgUpdatedAt = null
                    refreshEpg()
                }
                onSuccess()
            } catch (e: Exception) {
                loginError = e.message ?: "Falha ao conectar"
            } finally { loading = false }
        }
    }

    fun refreshEpg() {
        val s = session ?: return
        if (epgLoading) return
        viewModelScope.launch {
            epgLoading = true; epgMessage = null
            try {
                if (s.isDemo) epg = DemoData.epg()
                else epg = withContext(Dispatchers.IO) { XtreamApi.fetchEpg(s) }
                epgUpdatedAt = System.currentTimeMillis()
                epgMessage = "Guia atualizado: ${epg.values.sumOf { it.size }} programas"
            } catch (e: Exception) {
                epgMessage = "Erro ao atualizar EPG: ${e.message}"
            } finally { epgLoading = false }
        }
    }

    fun playItem(item: VodItem, onReady: (PlayerRequest) -> Unit) {
        val s = session
        if (item.url != null) { onReady(PlayerRequest(item.title, item.url, false)); return }
        val sid = item.seriesId ?: return
        viewModelScope.launch {
            try {
                val eps = withContext(Dispatchers.IO) { XtreamApi.seriesEpisodes(s!!, sid) }
                eps.firstOrNull()?.let { onReady(PlayerRequest("${item.title} · ${it.first}", it.second, false)) }
            } catch (_: Exception) { }
        }
    }

    fun toggleFavorite(key: String) { if (!favorites.remove(key)) favorites.add(key) }

    fun logout() { session = null; epg = emptyMap(); content = DemoData.content(); loginError = null }
}
