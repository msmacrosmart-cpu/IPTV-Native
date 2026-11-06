package com.example.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.epg.XmltvRepository
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.Episode
import com.example.iptv.data.model.EpgEntry
import com.example.iptv.data.model.LiveCategory
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.PlaybackRequest
import com.example.iptv.data.model.SeasonEpisodes
import com.example.iptv.data.model.SeriesCategory
import com.example.iptv.data.model.SeriesItem
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.VideoDecoder
import com.example.iptv.data.model.VodCategory
import com.example.iptv.data.model.VodItem
import com.example.iptv.data.repository.XtreamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TvNavSection(val label: String, val contentDescription: String) {
    HOME("Início", "Início"),
    LIVE_TV("TV ao Vivo", "TV ao Vivo"),
    MOVIES("Filmes", "Filmes"),
    SERIES("Séries", "Séries"),
    SETTINGS("Configurações", "Configurações")
}

/**
 * Holds the whole catalogue (live, VOD and series), the current filters and the playback
 * requests. All network work happens in [XtreamRepository]; this class only orchestrates state.
 */
class MainTvViewModel(
    private val repository: XtreamRepository,
    private val xmltv: XmltvRepository
) : ViewModel() {

    val account = repository.account
    val settings = repository.settings

    val section = MutableStateFlow(TvNavSection.HOME)

    // ------------------------------------------------------------------ live TV

    val liveCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val liveAll = MutableStateFlow<List<LiveChannel>>(emptyList())
    val liveLoading = MutableStateFlow(false)
    val liveError = MutableStateFlow<String?>(null)
    val liveCategoryFilter = MutableStateFlow<String?>(null)
    val liveQuery = MutableStateFlow("")

    val liveChannels: StateFlow<List<LiveChannel>> =
        combine(liveAll, liveCategoryFilter, liveQuery) { all, categoryId, query ->
            all.filter { channel ->
                val matchesCategory = categoryId == null || channel.categoryId == categoryId
                val matchesQuery = query.isBlank() || channel.name.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedChannel = MutableStateFlow<LiveChannel?>(null)
    val preview = MutableStateFlow<PlaybackRequest?>(null)

    val epgEntries = MutableStateFlow<List<EpgEntry>>(emptyList())
    val epgLoading = MutableStateFlow(false)
    val epgError = MutableStateFlow<String?>(null)
    val epgGuide = xmltv.guide
    val epgUpdateState = xmltv.state
    val accountRefreshing = MutableStateFlow(false)

    // ---------------------------------------------------------------- filmes/VOD

    val vodCategories = MutableStateFlow<List<VodCategory>>(emptyList())
    val vodAll = MutableStateFlow<List<VodItem>>(emptyList())
    val vodLoading = MutableStateFlow(false)
    val vodError = MutableStateFlow<String?>(null)
    val vodCategoryFilter = MutableStateFlow<String?>(null)
    val vodQuery = MutableStateFlow("")

    val vodItems: StateFlow<List<VodItem>> =
        combine(vodAll, vodCategoryFilter, vodQuery) { all, categoryId, query ->
            all.filter { item ->
                val matchesCategory = categoryId == null || item.categoryId == categoryId
                val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --------------------------------------------------------------------- series

    val seriesCategories = MutableStateFlow<List<SeriesCategory>>(emptyList())
    val seriesAll = MutableStateFlow<List<SeriesItem>>(emptyList())
    val seriesLoading = MutableStateFlow(false)
    val seriesError = MutableStateFlow<String?>(null)
    val seriesCategoryFilter = MutableStateFlow<String?>(null)
    val seriesQuery = MutableStateFlow("")

    val seriesItems: StateFlow<List<SeriesItem>> =
        combine(seriesAll, seriesCategoryFilter, seriesQuery) { all, categoryId, query ->
            all.filter { item ->
                val matchesCategory = categoryId == null || item.categoryId == categoryId
                val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedSeries = MutableStateFlow<SeriesItem?>(null)
    val seriesSeasons = MutableStateFlow<List<SeasonEpisodes>>(emptyList())
    val seriesDetailLoading = MutableStateFlow(false)
    val seriesDetailError = MutableStateFlow<String?>(null)

    // ------------------------------------------------------------------ playback

    val fullscreen = MutableStateFlow<PlaybackRequest?>(null)
    val alert = MutableStateFlow<String?>(null)
    val pingLatencyMillis = MutableStateFlow<Long?>(null)
    val pingLoading = MutableStateFlow(false)
    val epgSyncLabel = MutableStateFlow(repository.epgSyncLabel())

    private var liveJob: Job? = null
    private var vodJob: Job? = null
    private var seriesJob: Job? = null
    private var epgJob: Job? = null
    private var focusedChannelJob: Job? = null
    private var epgInitJob: Job? = null
    private var epgUpdateJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var pingJob: Job? = null
    private var seriesDetailJob: Job? = null

    // --------------------------------------------------------------- navigation

    fun selectSection(target: TvNavSection) {
        section.value = target
        if (target == TvNavSection.LIVE_TV && epgEntries.value.isEmpty()) {
            selectedChannel.value?.let { loadEpg(it) }
        }
    }

    // ------------------------------------------------------------------- loading

    fun refreshAll() {
        initializeEpg()
        loadLive(force = true)
        loadVod(force = true)
        loadSeries(force = true)
    }

    fun loadLive(force: Boolean = false) {
        if (liveJob?.isActive == true && !force) return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            liveLoading.value = true
            liveError.value = null
            coroutineScope {
                val categoriesDeferred = async { repository.liveCategories() }
                val channelsDeferred = async { repository.liveChannels() }
                val categoriesResult = categoriesDeferred.await()
                val channelsResult = channelsDeferred.await()

                when (categoriesResult) {
                    is ApiResult.Success -> liveCategories.value = categoriesResult.data
                    is ApiResult.Failure -> Unit
                }

                when (channelsResult) {
                    is ApiResult.Success -> {
                        liveAll.value = channelsResult.data
                        if (channelsResult.data.isEmpty()) {
                            liveError.value = "Nenhum canal foi retornado por este painel."
                        } else if (selectedChannel.value == null) {
                            selectChannel(channelsResult.data.first())
                        }
                    }

                    is ApiResult.Failure -> liveError.value = channelsResult.message
                }
            }
            liveLoading.value = false
        }
    }

    fun loadVod(force: Boolean = false) {
        if (vodJob?.isActive == true && !force) return
        vodJob?.cancel()
        vodJob = viewModelScope.launch {
            vodLoading.value = true
            vodError.value = null
            coroutineScope {
                val categoriesDeferred = async { repository.vodCategories() }
                val itemsDeferred = async { repository.vodItems() }
                val categoriesResult = categoriesDeferred.await()
                val itemsResult = itemsDeferred.await()

                if (categoriesResult is ApiResult.Success) {
                    vodCategories.value = categoriesResult.data
                }
                when (itemsResult) {
                    is ApiResult.Success -> {
                        vodAll.value = itemsResult.data
                        if (itemsResult.data.isEmpty()) {
                            vodError.value = "Nenhum filme encontrado nesta conta."
                        }
                    }

                    is ApiResult.Failure -> vodError.value = itemsResult.message
                }
            }
            vodLoading.value = false
        }
    }

    fun loadSeries(force: Boolean = false) {
        if (seriesJob?.isActive == true && !force) return
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            seriesLoading.value = true
            seriesError.value = null
            coroutineScope {
                val categoriesDeferred = async { repository.seriesCategories() }
                val itemsDeferred = async { repository.series() }
                val categoriesResult = categoriesDeferred.await()
                val itemsResult = itemsDeferred.await()

                if (categoriesResult is ApiResult.Success) {
                    seriesCategories.value = categoriesResult.data
                }
                when (itemsResult) {
                    is ApiResult.Success -> {
                        seriesAll.value = itemsResult.data
                        if (itemsResult.data.isEmpty()) {
                            seriesError.value = "Nenhuma série encontrada nesta conta."
                        }
                    }

                    is ApiResult.Failure -> seriesError.value = itemsResult.message
                }
            }
            seriesLoading.value = false
        }
    }

    // ------------------------------------------------------------------- live TV

    /** Focusing a row previews it after a short delay; OK/touch opens it immediately. */
    fun onChannelFocused(channel: LiveChannel) {
        focusedChannelJob?.cancel()
        if (selectedChannel.value?.id == channel.id) return
        focusedChannelJob = viewModelScope.launch {
            delay(400)
            setChannel(channel)
        }
    }

    fun selectChannel(channel: LiveChannel) {
        focusedChannelJob?.cancel()
        setChannel(channel)
    }

    private fun setChannel(channel: LiveChannel) {
        if (selectedChannel.value?.id == channel.id && preview.value != null) return
        selectedChannel.value = channel
        preview.value = livePlaybackRequest(channel)
        loadEpg(channel)
    }

    fun selectCategory(categoryId: String?) {
        if (liveCategoryFilter.value != categoryId) liveCategoryFilter.value = categoryId
    }

    fun loadEpg(channel: LiveChannel) {
        epgJob?.cancel()
        epgError.value = null
        val cached = xmltv.guide.value.forChannel(channel).filter { it.endMillis > System.currentTimeMillis() }
        if (cached.isNotEmpty()) {
            epgEntries.value = cached
            epgLoading.value = false
            return
        }
        epgEntries.value = emptyList()
        epgJob = viewModelScope.launch {
            epgLoading.value = true
            try {
                val result = repository.shortEpg(channel.id)
                if (selectedChannel.value?.id == channel.id) {
                    when (result) {
                        is ApiResult.Success -> epgEntries.value = result.data
                        is ApiResult.Failure -> epgError.value = result.message
                    }
                }
            } finally {
                if (selectedChannel.value?.id == channel.id) epgLoading.value = false
            }
        }
    }

    fun retryEpg() {
        selectedChannel.value?.let { loadEpg(it) }
    }

    fun openPreviewFullscreen() {
        preview.value?.let { fullscreen.value = it }
    }

    fun playChannelFullscreen(channel: LiveChannel) {
        selectChannel(channel)
        fullscreen.value = livePlaybackRequest(channel)
    }

    /** Zaps to the next/previous channel inside the fullscreen player. */
    fun zapChannel(forward: Boolean) {
        val current = fullscreen.value ?: return
        val channels = liveChannels.value.ifEmpty { liveAll.value }
        if (channels.isEmpty()) return

        val currentId = current.channelId
        val index = channels.indexOfFirst { it.id == currentId }
        val nextIndex = when {
            index < 0 -> 0
            forward -> (index + 1) % channels.size
            else -> (index - 1 + channels.size) % channels.size
        }
        val next = channels[nextIndex]
        selectChannel(next)
        fullscreen.value = livePlaybackRequest(next)
    }

    private fun livePlaybackRequest(channel: LiveChannel): PlaybackRequest {
        val format = settings.value.streamFormat
        val primary = repository.liveStreamUrl(channel.id, format)
        val alternative = repository.liveStreamUrl(channel.id, format.other())
        return PlaybackRequest(
            url = primary,
            fallbackUrl = alternative.takeIf { it.isNotBlank() && it != primary },
            title = channel.name,
            subtitle = "TV ao Vivo",
            isLive = true,
            channelId = channel.id
        )
    }

    // ------------------------------------------------------------------- filmes

    fun selectVodCategory(categoryId: String?) {
        vodCategoryFilter.value = categoryId
    }

    fun playMovie(item: VodItem) {
        val url = repository.movieStreamUrl(item)
        if (url.isBlank()) {
            alert.value = "Sessão inválida. Faça login novamente."
            return
        }
        fullscreen.value = PlaybackRequest(
            url = url,
            title = item.name,
            subtitle = "Filme",
            isLive = false
        )
    }

    // ------------------------------------------------------------------- séries

    fun selectSeriesCategory(categoryId: String?) {
        seriesCategoryFilter.value = categoryId
    }

    fun openSeriesDetail(series: SeriesItem) {
        selectedSeries.value = series
        seriesSeasons.value = emptyList()
        seriesDetailError.value = null
        seriesDetailLoading.value = true
        seriesDetailJob?.cancel()
        seriesDetailJob = viewModelScope.launch {
            val result = repository.seriesInfo(series.id)
            if (selectedSeries.value?.id != series.id) {
                seriesDetailLoading.value = false
                return@launch
            }
            when (result) {
                is ApiResult.Success -> {
                    seriesSeasons.value = result.data
                    if (result.data.isEmpty()) {
                        seriesDetailError.value = "Nenhum episódio disponível para esta série."
                    }
                }

                is ApiResult.Failure -> seriesDetailError.value = result.message
            }
            seriesDetailLoading.value = false
        }
    }

    fun closeSeriesDetail() {
        seriesDetailJob?.cancel()
        selectedSeries.value = null
        seriesSeasons.value = emptyList()
        seriesDetailError.value = null
    }

    fun playEpisode(series: SeriesItem, season: SeasonEpisodes, episode: Episode) {
        val url = repository.episodeStreamUrl(episode)
        if (url.isBlank()) {
            alert.value = "Sessão inválida. Faça login novamente."
            return
        }
        val subtitle = buildString {
            append("Temporada ")
            append(season.season)
            append(" · Episódio ")
            append(episode.number)
            if (episode.title.isNotBlank()) {
                append(" · ")
                append(episode.title)
            }
        }
        fullscreen.value = PlaybackRequest(
            url = url,
            title = series.name,
            subtitle = subtitle,
            isLive = false
        )
    }

    // ------------------------------------------------------------------ playback

    fun closeFullscreen() {
        fullscreen.value = null
    }

    fun consumeAlert() {
        alert.value = null
    }

    // ------------------------------------------------------------------ settings

    fun updateStreamFormat(format: StreamFormat) {
        repository.updateStreamFormat(format)
        selectedChannel.value?.let { preview.value = livePlaybackRequest(it) }
    }

    fun updateBufferSeconds(seconds: Int) {
        repository.updateBufferSeconds(seconds)
    }

    fun updateDecoder(decoder: VideoDecoder) {
        repository.updateDecoder(decoder)
    }

    private fun initializeEpg() {
        epgInitJob?.cancel()
        val credentials = repository.currentCredentials() ?: return
        epgInitJob = viewModelScope.launch {
            val preferences = settings.value
            val loaded = xmltv.loadFromDisk(credentials, preferences.customXmltvUrl)
            if (loaded) selectedChannel.value?.let { loadEpg(it) }
            val stale = System.currentTimeMillis() - preferences.lastEpgSyncMillis >= 12 * 60 * 60 * 1000L
            if (preferences.autoUpdateEpg && (!loaded || stale)) refreshEpg(showSuccess = false)
        }
    }

    fun refreshEpg(showSuccess: Boolean = true) {
        if (epgUpdateJob?.isActive == true) return
        val credentials = repository.currentCredentials()
        if (credentials == null) {
            alert.value = "Sessão não encontrada. Faça login novamente."
            return
        }
        val source = settings.value.customXmltvUrl
        epgUpdateJob = viewModelScope.launch {
            if (xmltv.refresh(credentials, source)) {
                repository.markEpgSynced()
                epgSyncLabel.value = repository.epgSyncLabel()
                selectedChannel.value?.let { loadEpg(it) }
                if (showSuccess) alert.value = "Guia de programação atualizado."
            }
        }
    }

    fun updateAutoEpg(enabled: Boolean) {
        repository.updateEpgAuto(enabled)
        if (enabled && settings.value.lastEpgSyncMillis == 0L) refreshEpg(showSuccess = false)
    }

    fun updateCustomXmltvUrl(url: String) {
        if (settings.value.customXmltvUrl == url) return
        epgInitJob?.cancel()
        epgUpdateJob?.cancel()
        epgJob?.cancel()
        xmltv.invalidate()
        repository.updateCustomXmltvUrl(url)
        epgSyncLabel.value = repository.epgSyncLabel()
        epgEntries.value = emptyList()
        epgLoading.value = false
        epgError.value = null
    }

    fun clearEpgCache() {
        epgInitJob?.cancel()
        epgUpdateJob?.cancel()
        xmltv.clear()
        repository.clearEpgSync()
        epgSyncLabel.value = repository.epgSyncLabel()
        selectedChannel.value?.let { loadEpg(it) }
    }

    fun refreshAccount() {
        if (accountRefreshJob?.isActive == true) return
        accountRefreshJob = viewModelScope.launch {
            accountRefreshing.value = true
            try {
                when (val result = repository.restoreSession()) {
                    is ApiResult.Success -> alert.value = "Dados da conta atualizados."
                    is ApiResult.Failure -> alert.value = result.message
                }
            } finally {
                accountRefreshing.value = false
            }
        }
    }

    fun testLatency() {
        if (pingLoading.value) return
        pingJob = viewModelScope.launch {
            pingLoading.value = true
            try {
                when (val result = repository.ping()) {
                    is ApiResult.Success -> {
                        pingLatencyMillis.value = result.data
                        alert.value = "Latência do painel: ${result.data} ms"
                    }
                    is ApiResult.Failure -> alert.value = result.message
                }
            } finally {
                pingLoading.value = false
            }
        }
    }

    fun logout() {
        reset()
        xmltv.clear()
        repository.logout()
        epgSyncLabel.value = repository.epgSyncLabel()
    }

    /** Clears every cached flow so a new login never shows the previous account's content. */
    fun reset() {
        liveJob?.cancel()
        vodJob?.cancel()
        seriesJob?.cancel()
        epgJob?.cancel()
        focusedChannelJob?.cancel()
        epgInitJob?.cancel()
        epgUpdateJob?.cancel()
        accountRefreshJob?.cancel()
        pingJob?.cancel()
        seriesDetailJob?.cancel()

        liveCategories.value = emptyList()
        liveAll.value = emptyList()
        liveError.value = null
        liveCategoryFilter.value = null
        liveQuery.value = ""
        selectedChannel.value = null
        preview.value = null
        epgEntries.value = emptyList()
        epgError.value = null
        epgLoading.value = false
        accountRefreshing.value = false
        pingLoading.value = false
        epgSyncLabel.value = repository.epgSyncLabel()

        vodCategories.value = emptyList()
        vodAll.value = emptyList()
        vodError.value = null
        vodCategoryFilter.value = null
        vodQuery.value = ""

        seriesCategories.value = emptyList()
        seriesAll.value = emptyList()
        seriesError.value = null
        seriesCategoryFilter.value = null
        seriesQuery.value = ""
        selectedSeries.value = null
        seriesSeasons.value = emptyList()
        seriesDetailError.value = null

        fullscreen.value = null
        alert.value = null
        pingLatencyMillis.value = null
        section.value = TvNavSection.HOME
    }

    class Factory(
        private val repository: XtreamRepository,
        private val xmltv: XmltvRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainTvViewModel::class.java)) {
                return MainTvViewModel(repository, xmltv) as T
            }
            throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
        }
    }
}
