package com.xtream.player.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.IPTVApplication
import com.example.iptv.data.cache.BackgroundSync
import com.example.iptv.data.cache.CachedCatalog
import com.example.iptv.data.cache.CachedShortGuideEntry
import com.example.iptv.data.cache.CatalogCache
import com.example.iptv.data.cache.HeroTarget
import com.example.iptv.data.epg.EpgUpdateState
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.Episode
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.VodDetails
import kotlinx.coroutines.Job
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
    private var heroPlayJob: Job? = null
    private var heroPrepJob: Job? = null
    private var generation = 0
    private var detailGeneration = 0
    private var heroPlayGeneration = 0
    private val shortGuide = mutableMapOf<String, List<Programme>>()
    private val shortGuideFetchedAt = mutableMapOf<String, Long>()

    var session by mutableStateOf<Session?>(null); private set
    var content by mutableStateOf(emptyContent()); private set
    var heroDetails by mutableStateOf<Map<String, VodDetails>>(emptyMap()); private set
    var loadingHeroKey by mutableStateOf<String?>(null); private set
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
            app.catalogCache.catalog.collect { cached ->
                val creds = repository.currentCredentials()
                if (cached != null && creds != null && session?.isDemo == false &&
                    cached.accountKey == CatalogCache.accountKey(creds) && session?.username == creds.username && session?.server == creds.serverUrl) {
                    val mapped = applyProgress(mapCatalog(cached, creds))
                    if (repository.currentCredentials() == creds) content = mapped
                }
            }
        }
        viewModelScope.launch {
            app.catalogCache.heroes.collect { cached ->
                val creds = repository.currentCredentials()
                if (cached != null && creds != null && session?.isDemo == false &&
                    cached.accountKey == CatalogCache.accountKey(creds) && session?.username == creds.username && session?.server == creds.serverUrl) {
                    heroDetails = cached.entries.mapValues { it.value.details }
                }
            }
        }
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
            val creds = repository.currentCredentials()!!
            activate(cached)
            val token = generation
            restoreJob = viewModelScope.launch {
                when (val result = repository.restoreSession()) {
                    is ApiResult.Success -> {
                        if (generation == token && repository.currentCredentials() == creds && session?.isDemo == false)
                            session = mapSession(result.data, creds)
                    }
                    is ApiResult.Failure -> if (generation == token && repository.currentCredentials() == creds)
                        notice = "Não foi possível validar a conta agora: ${result.message}"
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
                    val previous = repository.currentCredentials()
                    cancelActiveWork()
                    BackgroundSync.cancel(app)
                    app.catalogCache.forgetMemory()
                    repository.logout()
                    if (previous != null) viewModelScope.launch { app.catalogCache.deleteAccount(previous) }
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
        app.catalogCache.forgetMemory()
        BackgroundSync.schedule(app)
        library.select(creds)
        history = library.progress()
        favorites.clear(); favorites.addAll(library.favorites())
        shortGuide.clear(); shortGuideFetchedAt.clear()
        epg = emptyMap(); epgUpdatedAt = null; epgMessage = null
        content = emptyContent()
        session = mapSession(account, creds)
        val token = generation
        catalogueJob = viewModelScope.launch {
            // Show saved backdrops and catalog before considering the network. Playback URLs
            // are rebuilt with the current session rather than being stored in the snapshot.
            app.catalogCache.loadHeroes(creds)
            if (generation != token || repository.currentCredentials() != creds) return@launch
            val cached = app.catalogCache.load(creds)
            if (generation != token || repository.currentCredentials() != creds) return@launch
            if (cached?.isFresh() != true) {
                val result = app.catalogCache.refresh(creds)
                if (generation == token && repository.currentCredentials() == creds && result.failures > 0)
                    notice = "Não foi possível atualizar todo o catálogo. Os dados anteriores foram preservados."
            }
        }
        guideJob = viewModelScope.launch {
            val short = app.catalogCache.loadShortGuide(creds)
            if (generation != token || repository.currentCredentials() != creds) return@launch
            short.entries.forEach { (key, entry) ->
                shortGuide[key] = entry.programmes
                shortGuideFetchedAt[key] = entry.fetchedAtMillis
            }
            mergeGuide()
            val settings = repository.settings.value
            val loaded = xmltv.loadFromDisk(creds, settings.customXmltvUrl)
            if (generation != token || repository.currentCredentials() != creds) return@launch
            if (loaded) {
                epgUpdatedAt = settings.lastEpgSyncMillis.takeIf { it > 0 }
                mergeGuide()
            }
            val last = settings.lastEpgSyncMillis
            val guideFresh = last > 0 && System.currentTimeMillis() >= last &&
                System.currentTimeMillis() - last < BackgroundSync.GUIDE_TTL_MILLIS
            if (settings.autoUpdateEpg && (!loaded || !guideFresh)) updateGuide(creds, token)
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

    private fun mapCatalog(cached: CachedCatalog, creds: PanelCredentials): ContentState {
        val settings = repository.settings.value
        val account = repository.account.value
        val formats = account?.allowedFormats.orEmpty()
        val format = if (formats.isNotEmpty() && formats.none { it.contains("ts", true) } && account?.supportsHls == true)
            StreamFormat.HLS else settings.streamFormat
        val live = cached.channels.mapIndexed { i, channel ->
            Channel(channel.id.toString(), channel.streamNumber.takeIf { it > 0 } ?: (i + 1), channel.name, channel.icon,
                channel.categoryId.orEmpty(), channel.epgChannelId?.trim()?.lowercase(Locale.ROOT) ?: "stream:${channel.id}",
                repository.liveStreamUrl(channel.id, format, creds), repository.liveStreamUrl(channel.id, format.other(), creds))
        }
        val movies = cached.movies.map { movie ->
            VodItem(movie.id.toString(), movie.name, YEAR.find(movie.name)?.value.orEmpty(),
                movie.rating?.toDoubleOrNull() ?: (movie.rating5?.times(2)) ?: 0.0, "", movie.plot.orEmpty(),
                movie.icon, null, movie.categoryId.orEmpty(), repository.movieStreamUrl(movie, creds), addedAtMillis = movie.addedAtMillis)
        }
        val series = cached.series.map { show ->
            VodItem(show.id.toString(), show.name, show.releaseDate?.take(4).orEmpty(),
                show.rating?.toDoubleOrNull() ?: (show.rating5?.times(2)) ?: 0.0, show.genre.orEmpty(),
                show.plot.orEmpty(), show.cover, show.backdrop, show.categoryId.orEmpty(), null, show.id.toString())
        }
        return ContentState(cached.liveCategories.map { Category(it.id, it.name) }, live,
            cached.movieCategories.map { Category(it.id, it.name) }, movies,
            cached.seriesCategories.map { Category(it.id, it.name) }, series)
    }

    fun refreshContent() {
        if (session?.isDemo == true) {
            content = applyProgress(DemoData.content())
            notice = "Catálogo atualizado"
            return
        }
        val creds = repository.currentCredentials() ?: return
        if (session == null || catalogueJob?.isActive == true) return
        val token = generation
        catalogueJob = viewModelScope.launch {
            val result = app.catalogCache.refresh(creds, force = true)
            if (generation == token && repository.currentCredentials() == creds) {
                notice = if (result.failures == 0) "Catálogo atualizado" else
                    "Não foi possível atualizar todo o catálogo. Os dados anteriores foram preservados."
            }
        }
    }

    /** Fetch/cache artwork only for the latest six release movies, never the whole library. */
    fun prepareHeroItems(items: List<VodItem>) {
        val creds = repository.currentCredentials() ?: return
        if (session?.isDemo != false) return
        val targets = items.asSequence().filter { !it.isSeries }.take(6)
            .mapNotNull { item -> item.id.toIntOrNull()?.let { HeroTarget(it, item.poster) } }.toList()
        heroPrepJob?.cancel()
        heroPrepJob = viewModelScope.launch { app.heroLoader.prepare(creds, targets) }
    }

    private fun applyProgress(value: ContentState): ContentState {
        fun update(item: VodItem): VodItem = item.copy(progress = history[item.favoriteKey]?.fraction ?: item.progress)
        return value.copy(movies = value.movies.map(::update), series = value.series.map(::update))
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
        if (xmltv.refresh(creds, repository.settings.value.customXmltvUrl) && generation == token &&
            repository.markEpgSyncedIfActive(creds)) {
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
        val creds = repository.currentCredentials() ?: return
        shortEpgJob?.cancel()
        val token = generation
        shortEpgJob = viewModelScope.launch {
            delay(250)
            when (val result = repository.shortEpg(id)) {
                is ApiResult.Success -> if (generation == token && repository.currentCredentials() == creds) {
                    val now = System.currentTimeMillis()
                    val programs = result.data.map { Programme(key, it.title, it.startMillis, it.endMillis) }
                    // An empty/broken short response must not erase still-usable cached data.
                    if (programs.any { it.stop > now }) {
                        shortGuideFetchedAt[key] = now
                        shortGuide[key] = programs
                        mergeGuide()
                        app.catalogCache.saveShortGuide(creds, key, CachedShortGuideEntry(now, programs))
                    }
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
                    detailItem = detailItem?.withHeroDetails(details)
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

    /** Play a movie immediately, or resolve the last-watched/first episode for a series. */
    fun playHero(item: VodItem, onReady: (PlayerRequest) -> Unit) {
        if (!item.isSeries) { playItem(item, onReady); return }
        if (heroPlayJob?.isActive == true) return
        if (session?.isDemo == true) {
            playPreferredEpisode(item, DemoData.episodes(), onReady)
            return
        }
        val id = item.seriesId?.toIntOrNull() ?: run { notice = "Série indisponível."; return }
        val token = generation
        val playToken = ++heroPlayGeneration
        loadingHeroKey = item.favoriteKey
        heroPlayJob = viewModelScope.launch {
            try {
                when (val result = repository.seriesInfo(id)) {
                    is ApiResult.Success -> if (generation == token && heroPlayGeneration == playToken) {
                        val choices = result.data.flatMap { it.episodes }.map { EpisodeChoice(it, repository.episodeStreamUrl(it)) }
                            .sortedWith(compareBy({ it.episode.season }, { it.episode.number }))
                        heroPlayJob = null; loadingHeroKey = null
                        playPreferredEpisode(item, choices, onReady)
                    }
                    is ApiResult.Failure -> if (generation == token && heroPlayGeneration == playToken) notice = result.message
                }
            } finally {
                if (generation == token && heroPlayGeneration == playToken) { heroPlayJob = null; loadingHeroKey = null }
            }
        }
    }

    fun cancelHeroPlayback() {
        heroPlayGeneration++
        heroPlayJob?.cancel(); heroPlayJob = null; loadingHeroKey = null
    }

    private fun playPreferredEpisode(item: VodItem, choices: List<EpisodeChoice>, onReady: (PlayerRequest) -> Unit) {
        val savedId = history[item.favoriteKey]?.episodeId
        val choice = choices.firstOrNull { it.episode.id == savedId } ?: choices.firstOrNull()
        if (choice == null) { notice = "Nenhum episódio disponível para esta série."; return }
        onReady(episodeRequest(item, choice))
    }

    private fun episodeRequest(item: VodItem, choice: EpisodeChoice): PlayerRequest {
        val key = item.favoriteKey
        val saved = history[key]?.takeIf { it.episodeId == choice.episode.id }
        return PlayerRequest("${item.title} · ${choice.label}", choice.url, false,
            libraryKey = key, episodeId = choice.episode.id, resumePositionMs = saved?.position ?: 0L)
    }

    fun playSeries(onReady: (PlayerRequest) -> Unit) {
        val selection = episodeSelection ?: return
        val savedId = history[selection.item.favoriteKey]?.episodeId
        val choice = selection.choices.firstOrNull { it.episode.id == savedId } ?: selection.choices.firstOrNull()
        if (choice != null) playEpisode(choice, onReady)
    }

    fun playEpisode(choice: EpisodeChoice, onReady: (PlayerRequest) -> Unit) {
        val selection = episodeSelection ?: return
        onReady(episodeRequest(selection.item, choice))
    }

    fun saveProgress(request: PlayerRequest, position: Long, duration: Long) {
        val key = request.libraryKey ?: return
        if (duration <= 0) return
        val progress = library.saveProgress(key, position, duration, request.episodeId)
        history = if (progress == null) history - key else history + (key to progress)
        fun update(item: VodItem) = if (item.favoriteKey == key) item.copy(progress = progress?.fraction ?: 0f) else item
        content = content.copy(movies = content.movies.map(::update), series = content.series.map(::update))
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
        val previous = repository.currentCredentials()
        restoreJob?.cancel()
        cancelActiveWork()
        BackgroundSync.cancel(app)
        app.catalogCache.forgetMemory()
        xmltv.clear()
        repository.logout()
        if (previous != null) viewModelScope.launch { app.catalogCache.deleteAccount(previous) }
        session = null; content = emptyContent(); epg = emptyMap(); epgUpdatedAt = null
        epgLoading = false; epgMessage = null; loginError = null; notice = null; playerRequest = null; lastPlayedChannelId = null
        favorites.clear(); history = emptyMap(); shortGuide.clear(); shortGuideFetchedAt.clear()
    }

    private fun cancelActiveWork() {
        generation++; detailGeneration++
        catalogueJob?.cancel(); guideJob?.cancel(); shortEpgJob?.cancel(); episodeJob?.cancel()
        detailJob?.cancel()
        cancelHeroPlayback()
        heroPrepJob?.cancel(); heroPrepJob = null; heroDetails = emptyMap()
        detailItem = null; episodeSelection = null; episodeLoading = false; episodeError = null
    }

    companion object { private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b") }
}
