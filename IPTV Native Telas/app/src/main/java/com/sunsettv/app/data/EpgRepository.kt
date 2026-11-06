package com.sunsettv.app.data

import com.sunsettv.app.core.AppPrefs
import com.sunsettv.app.domain.EpgProgram
import com.sunsettv.app.domain.LiveChannel
import com.sunsettv.app.domain.XtreamCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface EpgUpdateState {
    object Idle : EpgUpdateState
    data class Downloading(val progress: Float?) : EpgUpdateState
    object Parsing : EpgUpdateState
    data class Error(val message: String) : EpgUpdateState
}

private const val HOUR = 3_600_000L

/** Baixa, lê (XMLTV), guarda em disco e serve o EPG. Fallback: get_short_epg por canal. */
class EpgRepository(
    private val client: XtreamClient,
    private val prefs: AppPrefs,
    cacheDir: File,
    private val scope: CoroutineScope,
) {
    private val file = File(cacheDir, "epg.xml")
    private val mutex = Mutex()

    @Volatile private var cache: Map<String, List<EpgProgram>> = emptyMap()

    private val _version = MutableStateFlow(0)
    /** Incrementa sempre que o cache muda (a UI usa para recompor). */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _state = MutableStateFlow<EpgUpdateState>(EpgUpdateState.Idle)
    val state: StateFlow<EpgUpdateState> = _state.asStateFlow()

    val hasData: Boolean get() = cache.isNotEmpty()

    /** Lê o último XMLTV baixado (rápido, sem rede). */
    suspend fun loadFromDisk() {
        if (cache.isNotEmpty() || !file.exists()) return
        mutex.withLock { runCatching { parseFile() } }
    }

    /** Dispara a atualização no escopo do app (não é cancelada ao sair da tela). */
    fun refreshAsync(c: XtreamCredentials) {
        val s = _state.value
        if (s is EpgUpdateState.Downloading || s is EpgUpdateState.Parsing) return
        scope.launch { refresh(c) }
    }

    suspend fun refresh(c: XtreamCredentials) = mutex.withLock {
        try {
            _state.value = EpgUpdateState.Downloading(null)
            val url = prefs.customXmltvUrl.ifBlank { client.xmltvUrl(c) }
            client.download(url, file) { _state.value = EpgUpdateState.Downloading(it) }
            _state.value = EpgUpdateState.Parsing
            parseFile()
            prefs.lastEpgUpdateMs = System.currentTimeMillis()
            _state.value = EpgUpdateState.Idle
            _version.update { it + 1 }
        } catch (e: CancellationException) {
            _state.value = EpgUpdateState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = EpgUpdateState.Error(e.message ?: "Falha ao atualizar o EPG")
        }
    }

    private suspend fun parseFile() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val r = XmltvParser.parse(file, now - 2 * HOUR, now + 48 * HOUR)
        cache = r.programs
        prefs.epgChannels = r.programs.size
        prefs.epgPrograms = r.programCount
        _version.update { it + 1 }
    }

    fun currentProgram(ch: LiveChannel, now: Long): EpgProgram? =
        ch.epgChannelId?.lowercase()?.let { cache[it] }?.firstOrNull { it.isLiveAt(now) }

    fun cachedPrograms(ch: LiveChannel, now: Long = System.currentTimeMillis()): List<EpgProgram> =
        ch.epgChannelId?.lowercase()?.let { cache[it] }?.filter { it.endMs > now }.orEmpty()

    suspend fun programsFor(ch: LiveChannel, c: XtreamCredentials): List<EpgProgram> {
        val cached = cachedPrograms(ch)
        if (cached.isNotEmpty()) return cached
        val now = System.currentTimeMillis()
        return runCatching { client.getShortEpg(c, ch.streamId) }
            .getOrDefault(emptyList())
            .filter { it.endMs > now }
    }
}
