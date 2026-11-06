package com.sunsettv.app.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunsettv.app.core.AppPrefs
import com.sunsettv.app.core.ServiceLocator
import com.sunsettv.app.core.SessionStore
import com.sunsettv.app.data.EpgRepository
import com.sunsettv.app.data.XtreamClient
import com.sunsettv.app.domain.EpgProgram
import com.sunsettv.app.domain.LiveCategory
import com.sunsettv.app.domain.LiveChannel
import com.sunsettv.app.domain.XtreamCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ALL_CATEGORIES = "__all__"
private const val PREVIEW_DEBOUNCE_MS = 600L
private const val EPG_STALE_MS = 12 * 3_600_000L

data class LiveTvUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val categories: List<LiveCategory> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val selectedCategoryId: String = ALL_CATEGORIES,
    val channels: List<LiveChannel> = emptyList(),
    /** Canal com foco do D-pad (atualiza o painel de informações na hora). */
    val focusedChannel: LiveChannel? = null,
    /** Canal tocando no preview (atualiza após um pequeno debounce para não "metralhar" o servidor). */
    val previewChannel: LiveChannel? = null,
    val programs: List<EpgProgram> = emptyList(),
)

class LiveTvViewModel(
    private val client: XtreamClient,
    private val session: SessionStore,
    private val epg: EpgRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvUiState())
    val state: StateFlow<LiveTvUiState> = _state.asStateFlow()
    val epgVersion: StateFlow<Int> = epg.version

    private var allChannels: List<LiveChannel> = emptyList()
    private var creds: XtreamCredentials? = null
    private var focusJob: Job? = null

    init {
        load()
        // Quando o EPG for atualizado, recarrega a programação do canal focado.
        viewModelScope.launch {
            epg.version.drop(1).collect {
                val ch = _state.value.focusedChannel ?: return@collect
                val cached = epg.cachedPrograms(ch)
                if (cached.isNotEmpty()) _state.update { s -> s.copy(programs = cached) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = LiveTvUiState(loading = true)
            val c = session.get()
            if (c == null) {
                _state.update { it.copy(loading = false, error = "Sessão não encontrada. Faça login novamente.") }
                return@launch
            }
            creds = c
            try {
                val (cats, chans) = coroutineScope {
                    val a = async { client.getLiveCategories(c) }
                    val b = async { client.getLiveChannels(c) }
                    a.await() to b.await()
                }
                allChannels = chans
                epg.loadFromDisk()
                val counts = chans.groupingBy { it.categoryId }.eachCount() + (ALL_CATEGORIES to chans.size)
                val first = chans.firstOrNull()
                _state.value = LiveTvUiState(
                    loading = false,
                    categories = cats.filter { (counts[it.id] ?: 0) > 0 },
                    counts = counts,
                    channels = chans,
                    focusedChannel = first,
                    previewChannel = first,
                    programs = first?.let { epg.cachedPrograms(it) }.orEmpty(),
                )
                first?.let { loadProgramsIfEmpty(it) }
                maybeAutoUpdateEpg(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Falha ao carregar os canais") }
            }
        }
    }

    fun selectCategory(id: String) {
        if (id == _state.value.selectedCategoryId) return
        val list = if (id == ALL_CATEGORIES) allChannels else allChannels.filter { it.categoryId == id }
        _state.update { it.copy(selectedCategoryId = id, channels = list) }
    }

    fun onChannelFocused(ch: LiveChannel) {
        _state.update { it.copy(focusedChannel = ch, programs = epg.cachedPrograms(ch)) }
        focusJob?.cancel()
        focusJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            _state.update { it.copy(previewChannel = ch) }
            loadProgramsIfEmpty(ch)
        }
    }

    fun currentProgram(ch: LiveChannel, now: Long): EpgProgram? = epg.currentProgram(ch, now)

    private suspend fun loadProgramsIfEmpty(ch: LiveChannel) {
        if (epg.cachedPrograms(ch).isNotEmpty()) return
        val c = creds ?: return
        val list = epg.programsFor(ch, c)
        _state.update { if (it.focusedChannel?.streamId == ch.streamId) it.copy(programs = list) else it }
    }

    private fun maybeAutoUpdateEpg(c: XtreamCredentials) {
        val stale = System.currentTimeMillis() - prefs.lastEpgUpdateMs > EPG_STALE_MS
        if (prefs.autoUpdateEpg && (!epg.hasData || stale)) epg.refreshAsync(c)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LiveTvViewModel(ServiceLocator.client, ServiceLocator.session, ServiceLocator.epg, ServiceLocator.prefs)
            }
        }
    }
}
