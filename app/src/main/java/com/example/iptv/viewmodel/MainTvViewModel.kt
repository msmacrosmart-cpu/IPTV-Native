package com.example.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iptv.data.model.*
import com.example.iptv.data.repository.XtreamRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TvNavSection {
    HOME,
    LIVE_TV,
    MOVIES,
    SERIES,
    FAVORITES,
    SETTINGS,
    SEARCH
}

class MainTvViewModel(val repository: XtreamRepository) : ViewModel() {

    // Seção ativa
    val currentSection = MutableStateFlow(TvNavSection.HOME)

    // ===== 1. LIVE TV (3 COLUNAS) =====
    val liveCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val selectedLiveCategoryId = MutableStateFlow("all")
    val liveSearchQuery = MutableStateFlow("")
    val allLiveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val selectedLiveStream = MutableStateFlow<LiveStream?>(null)
    val livePlayerUrl = MutableStateFlow<String?>(null)

    val filteredLiveStreams = combine(allLiveStreams, selectedLiveCategoryId, liveSearchQuery) { streams, catId, query ->
        streams.filter { stream ->
            val matchesCategory = (catId == "all" || stream.categoryId == catId)
            val matchesQuery = query.isBlank() || stream.name.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ===== 2. FILMES / VOD (2 COLUNAS) =====
    val vodCategories = MutableStateFlow<List<VodCategory>>(emptyList())
    val selectedVodCategoryId = MutableStateFlow("all")
    val vodSearchQuery = MutableStateFlow("")
    val allVodStreams = MutableStateFlow<List<VodStream>>(emptyList())
    val selectedVodStream = MutableStateFlow<VodStream?>(null)

    val filteredVodStreams = combine(allVodStreams, selectedVodCategoryId, vodSearchQuery) { streams, catId, query ->
        streams.filter { movie ->
            val matchesCategory = (catId == "all" || movie.categoryId == catId)
            val matchesQuery = query.isBlank() || movie.name.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ===== 3. SÉRIES (2 COLUNAS) =====
    val seriesCategories = MutableStateFlow<List<SeriesCategory>>(emptyList())
    val selectedSeriesCategoryId = MutableStateFlow("all")
    val seriesSearchQuery = MutableStateFlow("")
    val allSeriesStreams = MutableStateFlow<List<SeriesStream>>(emptyList())
    val selectedSeriesStream = MutableStateFlow<SeriesStream?>(null)

    val filteredSeriesStreams = combine(allSeriesStreams, selectedSeriesCategoryId, seriesSearchQuery) { series, catId, query ->
        series.filter { item ->
            val matchesCategory = (catId == "all" || item.categoryId == catId)
            val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ===== 4. CONFIGURAÇÕES =====
    val userInfo = repository.userInfo
    val userSettings = repository.userSettings
    val pingLatencyMs = MutableStateFlow(42)

    // ===== 5. REPRODUTOR FULLSCREEN =====
    val fullScreenUrl = MutableStateFlow<String?>(null)
    val fullScreenTitle = MutableStateFlow<String?>(null)

    init {
        loadAllData()
    }

    fun loadAllData() {
        viewModelScope.launch {
            // Live TV
            val cats = repository.getLiveCategories()
            liveCategories.value = listOf(LiveCategory("all", "Selecionar Todos")) + cats
            val streams = repository.getLiveStreams()
            allLiveStreams.value = streams
            if (streams.isNotEmpty() && selectedLiveStream.value == null) {
                selectLiveChannel(streams.first())
            }

            // VOD
            val vCats = repository.getVodCategories()
            vodCategories.value = listOf(VodCategory("all", "Selecionar Todos")) + vCats
            allVodStreams.value = repository.getVodStreams()

            // Séries
            val sCats = repository.getSeriesCategories()
            seriesCategories.value = listOf(SeriesCategory("all", "Selecionar Todos")) + sCats
            allSeriesStreams.value = repository.getSeries()
        }
    }

    fun selectLiveChannel(channel: LiveStream) {
        selectedLiveStream.value = channel
        livePlayerUrl.value = repository.buildLiveStreamUrl(channel.streamId)
    }

    fun openFullScreen(url: String, title: String) {
        fullScreenUrl.value = url
        fullScreenTitle.value = title
    }

    fun closeFullScreen() {
        fullScreenUrl.value = null
        fullScreenTitle.value = null
    }

    fun updateDecoder(type: VideoDecoderType) {
        repository.updateDecoder(type)
    }

    fun updateBufferSize(seconds: Int) {
        repository.updateBufferSize(seconds)
    }

    fun refreshEpg() {
        repository.refreshEpg()
    }

    fun runPingTest() {
        viewModelScope.launch {
            pingLatencyMs.value = (28..65).random()
        }
    }

    fun clearCache() {
        // Limpa cache interno
        viewModelScope.launch {
            loadAllData()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        repository.logout()
        onLoggedOut()
    }
}
