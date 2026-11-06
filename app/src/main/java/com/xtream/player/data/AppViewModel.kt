package com.xtream.player.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.IPTVApplication
import com.example.iptv.data.epg.EpgUpdateState
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.Episode
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.StreamFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Connects the native IPTVnator-style interface to the real Xtream session and XMLTV cache. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IPTVApplication
    private val repository = app.repository
    private val xmltv = app.epgRepository
    private val library = UserLibrary(application)
    private var history = emptyMap<String, UserLibrary.Progress>()
    private var catalogueJob: Job? = null
    private var guideJob: Job? = null
    private var shortEpgJob: Job? = null
    private var episodeJob: Job? = null
    private var detailJob: Job? = null
    private var restoreJob: Job? = null
    private var generation = 0
    private var detailGeneration = 0
    private val shortGuide = mutableMapOf<String, List<Programme>>()
    private val shortGuideFetchedAt = mutableMapOf<String, Long>()

    var session by mutableStateOf<Session?>(null); private set
    var content by mutableStateOf(emptyContent()); private set
    var epg by mutableStateOf<Map<String, List<Programme>>>(emptyMap()); private set
    var epgUpdatedAt by mutableStateOf<Long?>(null); private set
    var epgLoading by mutableStateOf(false); private set
    var epgMessage by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var restoring by mutableStateOf(true); private set
    var loginError by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var playerRequest by mutableStateOf<PlayerRequest?>(null); private set
    var lastPlayedChannelId by mutableStateOf<String?>(null); private set
    var episodeSelection by mutableStateOf<EpisodeSelection?>(null); private set
    var episodeLoading by mutableStateOf(false); private set
    var episodeError by mutableStateOf<String?>(null); private set
    var detailItem by mutableStateOf<VodItem?>(null); private set
    val favorites = mutableStateListOf<String>()

    data class EpisodeChoice(val episode: Episode, val url: String) {
        val label: String get() = "T${episode.season} E${episode.number} · ${episode.title}"
    }
    data class EpisodeSelection(val item: VodItem, val choices: List<EpisodeChoice>)

    init {
        viewModelScope.launch {
            xmltv.guide.collect { if (session?.isDemo == false) mergeGuide() }
        }
        viewModelScope.launch {
            xmltv.state.collect { state ->
                if (session?.isDemo == false) {
                    epgLoading = state is EpgUpdateState.Downloading || state is EpgUpdateState.Parsing
                    if (state is EpgUpdateState.Error) epgMessage = state.message
                }
            }
        }
        val cached = repository.cachedAccount()
        if (repository.hasStoredSession() && cached != null && repository.currentCredentials() != null) {
            activate(cached)
            restoreJob = viewModelScope.launch {
                when (val result = repository.restoreSession()) {
                    is ApiResult.Success -> {
                        if (session?.isDemo == false) session = mapSession(result.data, repository.currentCredentials()!!)
                    }
                    is ApiResult.Failure -> notice = "Não foi possível validar a conta agora: ${result.message}"
                }
            }
        }
        restoring = false
    }

    fun login(server: String, user: String, pass: String, onSuccess: () -> Unit) {
        if (loading) return
        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            loginError = "Preencha servidor, usuário e senha"
            return
        }
        restoreJob?.cancel()
        viewModelScope.launch {
            loading = true
            loginError = null
            try {
                if (server.trim().equals("demo", true)) {
                    repository.logout()
                    cancelActiveWork()
                    xmltv.invalidate()
                    library.select(null)
                    history = library.progress()
                    favorites.clear(); favorites.addAll(library.favorites())
                    session = DemoData.session()
                    content = applyProgress(DemoData.content())
                    epg = DemoData.epg()
                    epgUpdatedAt = System.currentTimeMillis()
                    epgMessage = null
                    onSuccess()
                } else {
                    when (val result = repository.login(server, user, pass)) {
                        is ApiResult.Success -> { activate(result.data); onSuccess() }
                        is ApiResult.Failure -> loginError = result.message
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun activate(account: AccountInfo) {
        val creds = repository.currentCredentials() ?: return
        cancelActiveWork()
        xmltv.invalidate()
        library.select(creds)
        history = library.progress()
        favorites.clear(); favorites.addAll(library.favorites())
        shortGuide.clear(); shortGuideFetchedAt.clear()
        epg = emptyMap(); epgUpdatedAt = null; epgMessage = null
        content = emptyContent()
        session = mapSession(account, creds)
        refreshContent()
        val token = generation
        guideJob = viewModelScope.launch {
            val settings = repository.settings.value
            val loaded = xmltv.loadFromDisk(creds, settings.customXmltvUrl)
            if (generation != token) return@launch
            if (loaded) {
                epgUpdatedAt = settings.lastEpgSyncMillis.takeIf { it > 0 }
                mergeGuide()
            }
            if (settings.autoUpdateEpg && (!loaded || System.currentTimeMillis() - settings.lastEpgSyncMillis > 12 * 3_600_000L)) {
                updateGuide(creds, token)
            }
        }
    }

    private fun mapSession(account: AccountInfo, creds: PanelCredentials): Session {
        // Do not display query parameters from custom guide URLs, which may contain tokens.
        val customSource = repository.settings.value.customXmltvUrl.toHttpUrlOrNull()?.newBuilder()
            ?.username("")?.password("")?.query(null)?.fragment(null)?.build()?.toString().orEmpty()
        val source = customSource.ifBlank { "${creds.serverUrl.trimEnd('/')}/xmltv.php" }
        return Session(creds.serverUrl, creds.username, creds.password, account.statusLabel,
            account.expiresAtMillis, account.createdAtMillis, account.activeConnections,
            account.maxConnections, epgSource = source)
    }

    fun refreshContent() {
        if (session?.isDemo == true) {
            content = applyProgress(DemoData.content())
            notice = "Catálogo atualizado"
            return
        }
        if (session == null || catalogueJob?.isActive == true) return
        val token = generation
        val previous = content
        catalogueJob = viewModelScope.launch {
            val next = coroutineScope {
                val lc = async { repository.liveCategories() }
                val ch = async { repository.liveChannels() }
                val mc = async { repository.vodCategories() }
                val mv = async { repository.vodItems() }
                val sc = async { repository.seriesCategories() }
                val se = async { repository.series() }
                val liveCats = lc.await(); val channels = ch.await(); val movieCats = mc.await()
                val movies = mv.await(); val seriesCats = sc.await(); val series = se.await()
                val errors = listOf(liveCats, channels, movieCats, movies, seriesCats, series).count { it is ApiResult.Failure }
                if (errors > 0 && generation == token) notice = "Não foi possível carregar parte do catálogo. Tente atualizar."
                val settings = repository.settings.value
                val account = repository.account.value
                val formats = account?.allowedFormats.orEmpty()
                val format = if (formats.isNotEmpty() && formats.none { it.contains("ts", true) } && account?.supportsHls == true)
                    StreamFormat.HLS else settings.streamFormat
                val live = channels.getOrNull()?.mapIndexed { i, c ->
                    Channel(c.id.toString(), c.streamNumber.takeIf { it > 0 } ?: (i + 1), c.name, c.icon,
                        c.categoryId.orEmpty(), c.epgChannelId?.trim()?.lowercase(Locale.ROOT) ?: "stream:${c.id}",
                        repository.liveStreamUrl(c.id, format), repository.liveStreamUrl(c.id, format.other()))
                } ?: previous.channels
                val vod = movies.getOrNull()?.map { m ->
                    VodItem(m.id.toString(), m.name, YEAR.find(m.name)?.value.orEmpty(),
                        m.rating?.toDoubleOrNull() ?: (m.rating5?.times(2)) ?: 0.0, "", m.plot.orEmpty(),
                        m.icon, null, m.categoryId.orEmpty(), repository.movieStreamUrl(m))
                } ?: previous.movies
                val shows = series.getOrNull()?.map { s ->
                    VodItem(s.id.toString(), s.name, s.releaseDate?.take(4).orEmpty(),
                        s.rating?.toDoubleOrNull() ?: (s.rating5?.times(2)) ?: 0.0, s.genre.orEmpty(),
                        s.plot.orEmpty(), s.cover, s.backdrop, s.categoryId.orEmpty(), null, s.id.toString())
                } ?: previous.series
                ContentState(liveCats.getOrNull()?.map { Category(it.id, it.name) } ?: previous.liveCategories, live,
                    movieCats.getOrNull()?.map { Category(it.id, it.name) } ?: previous.movieCategories, vod,
                    seriesCats.getOrNull()?.map { Category(it.id, it.name) } ?: previous.seriesCategories, shows,
                    if (movies is ApiResult.Failure) previous.hero else
                        vod.filter { it.poster != null }.maxByOrNull { it.rating } ?: vod.firstOrNull())
            }
            if (generation != token) return@launch
            content = applyProgress(next)
            // The catalog response often omits plot, genre and backdrop; enrich only the hero.
            val hero = content.hero
            val heroId = hero?.id?.toIntOrNull()
            if (hero != null && heroId != null) {
                val details = repository.vodDetails(heroId).getOrNull()
                if (details != null && generation == token && content.hero?.id == hero.id) {
                    content = content.copy(hero = hero.copy(
                        plot = details.plot ?: hero.plot, genre = details.genre ?: hero.genre,
                        year = details.releaseDate?.take(4) ?: hero.year,
                        backdrop = details.backdrop ?: hero.backdrop,
                        rating = details.rating?.toDoubleOrNull() ?: hero.rating,
                    ))
                }
            }
        }
    }

    private fun applyProgress(value: ContentState): ContentState {
        fun update(item: VodItem): VodItem = item.copy(progress = history[item.favoriteKey]?.fraction ?: item.progress)
        return value.copy(movies = value.movies.map(::update), series = value.series.map(::update), hero = value.hero?.let(::update))
    }

    fun refreshEpg() {
        val active = session ?: return
        if (active.isDemo) {
            epg = DemoData.epg(); epgUpdatedAt = System.currentTimeMillis()
            epgMessage = "Guia atualizado: ${epg.values.sumOf { it.size }} programas"
            return
        }
        val creds = repository.currentCredentials() ?: return
        if (guideJob?.isActive == true || epgLoading) return
        val token = generation
        guideJob = viewModelScope.launch { updateGuide(creds, token) }
    }

    private suspend fun updateGuide(creds: PanelCredentials, token: Int) {
        epgMessage = null
        if (xmltv.refresh(creds, repository.settings.value.customXmltvUrl) && generation == token) {
            repository.markEpgSynced()
            epgUpdatedAt = repository.settings.value.lastEpgSyncMillis
            mergeGuide()
            epgMessage = "Guia atualizado: ${epg.values.sumOf { it.size }} programas"
        }
    }

    private fun mergeGuide() {
        val xml = xmltv.guide.value.programs.mapValues { (key, entries) ->
            entries.map { Programme(key, it.title, it.startMillis, it.endMillis) }
        }
        // XMLTV wins when it has usable data for the channel; short EPG fills gaps.
        val now = System.currentTimeMillis()
        epg = shortGuide.toMap() + xml.filterValues { programmes -> programmes.any { it.stop > now } }
    }

    fun channelFocused(channel: Channel) {
        if (session == null || session?.isDemo == true) return
        val key = channel.epgId ?: return
        if (epg[key].orEmpty().any { it.stop > System.currentTimeMillis() }) return
        if (System.currentTimeMillis() - (shortGuideFetchedAt[key] ?: 0L) < 10 * 60_000L) return
        val id = channel.id.toIntOrNull() ?: return
        shortEpgJob?.cancel()
        val token = generation
        shortEpgJob = viewModelScope.launch {
            delay(250)
            when (val result = repository.shortEpg(id)) {
                is ApiResult.Success -> if (generation == token) {
                    shortGuideFetchedAt[key] = System.currentTimeMillis()
                    shortGuide[key] = result.data.map { Programme(key, it.title, it.startMillis, it.endMillis) }
                    mergeGuide()
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun openDetails(item: VodItem) {
        detailJob?.cancel(); episodeJob?.cancel()
        val detailToken = ++detailGeneration
        detailItem = item
        episodeSelection = null
        episodeError = null
        episodeLoading = false
        if (item.isSeries) {
            if (session?.isDemo == true) {
                episodeSelection = EpisodeSelection(item, DemoData.episodes())
                return
            }
            val id = item.seriesId?.toIntOrNull()
            if (id == null) { episodeError = "Série indisponível."; return }
            val token = generation
            episodeLoading = true
            episodeJob = viewModelScope.launch {
                try {
                    when (val result = repository.seriesInfo(id)) {
                        is ApiResult.Success -> if (generation == token && detailGeneration == detailToken && detailItem?.favoriteKey == item.favoriteKey) {
                            val choices = result.data.flatMap { it.episodes }.map { EpisodeChoice(it, repository.episodeStreamUrl(it)) }
                                .sortedWith(compareBy({ it.episode.season }, { it.episode.number }))
                            episodeSelection = EpisodeSelection(item, choices)
                            if (choices.isEmpty()) episodeError = "Nenhum episódio disponível para esta série."
                        }
                        is ApiResult.Failure -> if (generation == token && detailGeneration == detailToken && detailItem?.favoriteKey == item.favoriteKey) episodeError = result.message
                    }
                } finally {
                    if (generation == token && detailGeneration == detailToken && detailItem?.favoriteKey == item.favoriteKey) episodeLoading = false
                }
            }
        } else if (session?.isDemo != true) {
            val id = item.id.toIntOrNull() ?: return
            val token = generation
            detailJob = viewModelScope.launch {
                val details = repository.vodDetails(id).getOrNull()
                if (details != null && generation == token && detailGeneration == detailToken && detailItem?.favoriteKey == item.favoriteKey) {
                    detailItem = item.copy(plot = details.plot ?: item.plot, genre = details.genre ?: item.genre,
                        year = details.releaseDate?.take(4) ?: item.year, backdrop = details.backdrop ?: item.backdrop,
                        rating = details.rating?.toDoubleOrNull() ?: item.rating)
                }
            }
        }
    }

    fun closeDetails() {
        detailGeneration++
        detailJob?.cancel(); episodeJob?.cancel()
        detailItem = null; episodeSelection = null; episodeLoading = false; episodeError = null
    }

    fun playItem(item: VodItem, onReady: (PlayerRequest) -> Unit) {
        val url = item.url ?: run { notice = "Este item não possui vídeo disponível."; return }
        val saved = history[item.favoriteKey]
        onReady(PlayerRequest(item.title, url, false, libraryKey = item.favoriteKey,
            resumePositionMs = saved?.position ?: 0L))
    }

    fun playSeries(onReady: (PlayerRequest) -> Unit) {
        val selection = episodeSelection ?: return
        val savedId = history[selection.item.favoriteKey]?.episodeId
        val choice = selection.choices.firstOrNull { it.episode.id == savedId } ?: selection.choices.firstOrNull()
        if (choice != null) playEpisode(choice, onReady)
    }

    fun playEpisode(choice: EpisodeChoice, onReady: (PlayerRequest) -> Unit) {
        val selection = episodeSelection ?: return
        val key = selection.item.favoriteKey
        val saved = history[key]?.takeIf { it.episodeId == choice.episode.id }
        onReady(PlayerRequest("${selection.item.title} · ${choice.label}", choice.url, false,
            libraryKey = key, episodeId = choice.episode.id, resumePositionMs = saved?.position ?: 0L))
    }

    fun saveProgress(request: PlayerRequest, position: Long, duration: Long) {
        val key = request.libraryKey ?: return
        if (duration <= 0) return
        val progress = library.saveProgress(key, position, duration, request.episodeId)
        history = if (progress == null) history - key else history + (key to progress)
        fun update(item: VodItem) = if (item.favoriteKey == key) item.copy(progress = progress?.fraction ?: 0f) else item
        content = content.copy(movies = content.movies.map(::update), series = content.series.map(::update),
            hero = content.hero?.let(::update))
        detailItem = detailItem?.let(::update)
    }

    fun toggleFavorite(key: String) {
        if (!favorites.remove(key)) favorites.add(key)
        library.saveFavorites(favorites)
    }

    fun consumeNotice() { notice = null }

    fun startPlayback(request: PlayerRequest) {
        playerRequest = request
        lastPlayedChannelId = if (request.isLive) request.playlist.getOrNull(request.index)?.id else null
    }
    fun channelPlaying(channel: Channel) { lastPlayedChannelId = channel.id }
    fun consumeChannelRestore() { lastPlayedChannelId = null }

    fun logout() {
        restoreJob?.cancel()
        cancelActiveWork()
        xmltv.clear()
        repository.logout()
        session = null; content = emptyContent(); epg = emptyMap(); epgUpdatedAt = null
        epgLoading = false; epgMessage = null; loginError = null; notice = null; playerRequest = null; lastPlayedChannelId = null
        favorites.clear(); history = emptyMap(); shortGuide.clear(); shortGuideFetchedAt.clear()
    }

    private fun cancelActiveWork() {
        generation++; detailGeneration++
        catalogueJob?.cancel(); guideJob?.cancel(); shortEpgJob?.cancel(); episodeJob?.cancel()
        detailJob?.cancel()
        detailItem = null; episodeSelection = null; episodeLoading = false; episodeError = null
    }

    companion object { private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b") }
}
