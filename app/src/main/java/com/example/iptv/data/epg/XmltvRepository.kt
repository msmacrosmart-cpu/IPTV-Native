package com.example.iptv.data.epg

import com.example.iptv.data.model.EpgEntry
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.PanelCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

sealed interface EpgUpdateState {
    data object Idle : EpgUpdateState
    data class Downloading(val progress: Float?) : EpgUpdateState
    data object Parsing : EpgUpdateState
    data class Error(val message: String) : EpgUpdateState
}

data class EpgGuide(
    val sourceKey: String = "",
    val programs: Map<String, List<EpgEntry>> = emptyMap(),
    val programCount: Int = 0,
    val cachedAtMillis: Long = 0,
) {
    val channelCount: Int get() = programs.size
    fun forChannel(channel: LiveChannel): List<EpgEntry> =
        channel.epgChannelId?.trim()?.lowercase(Locale.ROOT)?.let { programs[it] }.orEmpty()
}

/**
 * XMLTV cache shared by TV and settings. Downloads into a temporary file and only replaces
 * the last usable cache after a successful parse. Each account/source has its own cache key.
 */
class XmltvRepository(cacheDir: File, private val http: OkHttpClient) {
    private val directory = File(cacheDir, "xmltv")
    private val mutex = Mutex()
    private val generation = AtomicLong(0)
    private val stateLock = Any()
    private val _guide = MutableStateFlow(EpgGuide())
    val guide: StateFlow<EpgGuide> = _guide.asStateFlow()
    private val _state = MutableStateFlow<EpgUpdateState>(EpgUpdateState.Idle)
    val state: StateFlow<EpgUpdateState> = _state.asStateFlow()

    suspend fun loadFromDisk(credentials: PanelCredentials, customUrl: String): Boolean {
        val url = try { sourceUrl(credentials, customUrl) } catch (_: IllegalArgumentException) { return false }
        val key = cacheKey(credentials, url)
        val token = generation.get()
        return mutex.withLock {
            if (token != generation.get()) return@withLock false
            if (_guide.value.sourceKey == key) return@withLock true
            synchronized(stateLock) {
                if (token != generation.get()) return@withLock false
                _guide.value = EpgGuide()
            }
            val file = File(directory, "$key.xml")
            if (!file.isFile) return@withLock false
            try {
                val result = parse(file)
                synchronized(stateLock) {
                    if (token != generation.get()) return@withLock false
                    _guide.value = EpgGuide(key, result.programs, result.programCount, file.lastModified())
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun refresh(credentials: PanelCredentials, customUrl: String): Boolean {
        val token = generation.get()
        return mutex.withLock {
            if (token != generation.get()) return@withLock false
            var temp: File? = null
            try {
                val url = sourceUrl(credentials, customUrl)
                val key = cacheKey(credentials, url)
                synchronized(stateLock) {
                    if (token != generation.get()) return@withLock false
                    if (_guide.value.sourceKey != key) _guide.value = EpgGuide()
                }
                directory.mkdirs()
                val downloaded = File.createTempFile("guide-", ".tmp", directory)
                temp = downloaded
                if (!setState(token, EpgUpdateState.Downloading(null))) return@withLock false
                download(url, downloaded, token)
                currentCoroutineContext().ensureActive()
                if (!setState(token, EpgUpdateState.Parsing)) return@withLock false
                val result = parse(downloaded)
                currentCoroutineContext().ensureActive()
                if (result.programCount == 0 && _guide.value.sourceKey == key && _guide.value.programCount > 0)
                    throw IOException("O EPG recebido não contém programação válida; o guia anterior foi preservado.")
                synchronized(stateLock) {
                    if (token != generation.get()) return@withLock false
                    val target = File(directory, "$key.xml")
                    if (!downloaded.renameTo(target)) throw IOException("Não foi possível salvar o guia no dispositivo.")
                    _guide.value = EpgGuide(key, result.programs, result.programCount, target.lastModified())
                    _state.value = EpgUpdateState.Idle
                }
                true
            } catch (e: CancellationException) {
                synchronized(stateLock) { if (token == generation.get()) _state.value = EpgUpdateState.Idle }
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is IllegalArgumentException -> "URL XMLTV inválida. Informe um endereço HTTP ou HTTPS."
                    is IOException -> e.message ?: "Não foi possível baixar o guia de programação."
                    else -> "Não foi possível ler o guia XMLTV. Confira a fonte configurada."
                }
                synchronized(stateLock) { if (token == generation.get()) _state.value = EpgUpdateState.Error(message) }
                false
            } finally {
                temp?.delete()
            }
        }
    }

    /** Invalidates ongoing work and in-memory data when the source changes. */
    fun invalidate() = synchronized(stateLock) {
        generation.incrementAndGet()
        _guide.value = EpgGuide()
        _state.value = EpgUpdateState.Idle
    }

    /** Also removes every account's cached XMLTV file when logging out or clearing cache. */
    fun clear() = synchronized(stateLock) {
        generation.incrementAndGet()
        _guide.value = EpgGuide()
        _state.value = EpgUpdateState.Idle
        directory.deleteRecursively()
    }

    private fun setState(token: Long, state: EpgUpdateState): Boolean = synchronized(stateLock) {
        if (token != generation.get()) false else {
            _state.value = state
            true
        }
    }

    private suspend fun parse(file: File): XmltvParser.Result = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val context = currentCoroutineContext()
        XmltvParser.parse(file, now - 2 * HOUR, now + 48 * HOUR) { context.ensureActive() }
    }

    private suspend fun download(url: String, file: File, token: Long) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val call = http.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("O servidor do EPG respondeu HTTP ${response.code}.")
                val body = response.body ?: throw IOException("O servidor do EPG enviou uma resposta vazia.")
                val total = body.contentLength()
                if (total > MAX_DOWNLOAD_BYTES) throw IOException("O arquivo de EPG é grande demais para este dispositivo.")
                body.byteStream().use { input ->
                    file.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = 0L
                        var lastPercent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (token != generation.get()) throw CancellationException("Fonte de EPG alterada")
                            val read = input.read(buffer)
                            if (read < 0) break
                            bytes += read
                            if (bytes > MAX_DOWNLOAD_BYTES) throw IOException("O arquivo de EPG é grande demais para este dispositivo.")
                            output.write(buffer, 0, read)
                            if (total > 0) {
                                val percent = ((bytes * 100 / total).coerceIn(0, 100)).toInt()
                                if (percent != lastPercent) {
                                    if (!setState(token, EpgUpdateState.Downloading(percent / 100f))) {
                                        throw CancellationException("Fonte de EPG alterada")
                                    }
                                    lastPercent = percent
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            // Network exceptions can contain sensitive URLs, so do not surface them verbatim.
            if (e.message?.startsWith("O servidor do EPG") == true ||
                e.message?.startsWith("O arquivo de EPG") == true
            ) throw e
            throw IOException("Falha ao baixar o EPG. Verifique a conexão e a fonte configurada.", e)
        }
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024

        fun sourceUrl(credentials: PanelCredentials, customUrl: String): String {
            if (customUrl.isNotBlank()) {
                return customUrl.trim().toHttpUrlOrNull()?.toString()
                    ?: throw IllegalArgumentException("URL XMLTV inválida")
            }
            val base = credentials.serverUrl.trim().let { if (it.endsWith('/')) it else "$it/" }
                .toHttpUrlOrNull() ?: throw IllegalArgumentException("Servidor inválido")
            return base.newBuilder()
                .addPathSegment("xmltv.php")
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .build().toString()
        }

        private fun cacheKey(credentials: PanelCredentials, sourceUrl: String): String {
            val input = "${credentials.serverUrl}\u0000${credentials.username}\u0000$sourceUrl"
            return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}
