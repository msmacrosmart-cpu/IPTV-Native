package com.example.iptv.data.cache

import android.util.AtomicFile
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.LiveCategory
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.SeriesCategory
import com.example.iptv.data.model.SeriesItem
import com.example.iptv.data.model.VodCategory
import com.example.iptv.data.model.VodDetails
import com.example.iptv.data.model.VodItem
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.network.PanelUrl
import com.example.iptv.data.repository.XtreamRepository
import com.xtream.player.data.Programme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

const val CATALOG_TTL_MILLIS = 3L * 24 * 60 * 60 * 1000

fun cacheIsFresh(updatedAtMillis: Long, now: Long = System.currentTimeMillis(), intervalDays: Int = 3): Boolean =
    updatedAtMillis > 0 && now >= updatedAtMillis &&
        now - updatedAtMillis < intervalDays.coerceIn(1, 3) * 24L * 60 * 60 * 1000

/** A snapshot contains provider data only. Playback URLs are reconstructed from the current
 * encrypted session instead of storing the password in each channel/movie entry. */
@Serializable
data class CachedCatalog(
    val accountKey: String,
    val updatedAtMillis: Long = 0,
    val complete: Boolean = false,
    val liveCategories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannel> = emptyList(),
    val movieCategories: List<VodCategory> = emptyList(),
    val movies: List<VodItem> = emptyList(),
    val seriesCategories: List<SeriesCategory> = emptyList(),
    val series: List<SeriesItem> = emptyList(),
) {
    fun isFresh(now: Long = System.currentTimeMillis(), intervalDays: Int = 3): Boolean =
        complete && cacheIsFresh(updatedAtMillis, now, intervalDays)
}

@Serializable
data class CachedHero(
    val fetchedAtMillis: Long,
    val details: VodDetails,
    // Older snapshots did not contain TMDB artwork; enrich them once after upgrading.
    val tmdbChecked: Boolean = false,
)

@Serializable
data class CachedHeroes(val accountKey: String, val entries: Map<String, CachedHero> = emptyMap())

@Serializable
data class CachedShortGuide(val accountKey: String, val entries: Map<String, CachedShortGuideEntry> = emptyMap())

@Serializable
data class CachedShortGuideEntry(val fetchedAtMillis: Long, val programmes: List<Programme>)

data class CatalogRefreshResult(val snapshot: CachedCatalog?, val failures: Int, val attempted: Boolean)

/** A phase represents real requests, not a timer or a fabricated percentage. */
enum class CatalogStage(val label: String) {
    PLAYLIST("Baixando Playlist"),
    CHANNELS("Baixando Canais"),
    MOVIES("Baixando Filmes"),
    SERIES("Baixando Séries"),
}

/**
 * App-private, per-account catalog/hero/short-EPG cache. Only one refresh runs at a time;
 * reading the last snapshot never waits for an in-flight network request. Files are compressed,
 * bounded and atomically replaced. A failed pair of endpoints retains that whole previous section.
 */
class CatalogCache(private val directory: File, private val repository: XtreamRepository) {
    private val refreshMutex = Mutex()
    private val fileMutex = Mutex()
    private val _catalog = MutableStateFlow<CachedCatalog?>(null)
    val catalog: StateFlow<CachedCatalog?> = _catalog.asStateFlow()
    private val _heroes = MutableStateFlow<CachedHeroes?>(null)
    val heroes: StateFlow<CachedHeroes?> = _heroes.asStateFlow()

    suspend fun load(credentials: PanelCredentials): CachedCatalog? = fileMutex.withLock {
        val key = accountKey(credentials)
        val cached = _catalog.value?.takeIf { it.accountKey == key } ?: read<CachedCatalog>(file(key, "catalog"))
        val valid = cached?.takeIf { it.accountKey == key }
        if (repository.currentCredentials() == credentials && valid != null) _catalog.value = valid
        valid
    }

    suspend fun loadHeroes(credentials: PanelCredentials): CachedHeroes = fileMutex.withLock {
        val key = accountKey(credentials)
        val cached = _heroes.value?.takeIf { it.accountKey == key } ?: read<CachedHeroes>(file(key, "heroes"))
        val valid = cached?.takeIf { it.accountKey == key } ?: CachedHeroes(key)
        if (repository.currentCredentials() == credentials) _heroes.value = valid
        valid
    }

    suspend fun mergeHeroes(credentials: PanelCredentials, updates: Map<String, CachedHero>, sessionToken: Long) {
        if (updates.isEmpty() || !repository.isSessionCurrent(credentials, sessionToken)) return
        val key = accountKey(credentials)
        fileMutex.withLock {
            if (!repository.isSessionCurrent(credentials, sessionToken)) return@withLock
            val current = _heroes.value?.takeIf { it.accountKey == key }
                ?: read<CachedHeroes>(file(key, "heroes"))?.takeIf { it.accountKey == key }
                ?: CachedHeroes(key)
            // Limit old hero entries on inexpensive TV sticks; current candidates remain newest.
            val entries = (current.entries + updates).entries.sortedWith(
                compareByDescending<Map.Entry<String, CachedHero>> { it.key in updates }
                    .thenByDescending { it.value.fetchedAtMillis })
                .take(MAX_HERO_ENTRIES).associate { it.key to it.value }
            val next = CachedHeroes(key, entries)
            if (write(file(key, "heroes"), next) && repository.isSessionCurrent(credentials, sessionToken)) _heroes.value = next
        }
    }

    suspend fun loadShortGuide(credentials: PanelCredentials, offsetMillis: Long = 0): CachedShortGuide {
        val key = accountKey(credentials)
        val cached = fileMutex.withLock { read<CachedShortGuide>(file(key, "short-epg")) }
        val now = System.currentTimeMillis()
        val entries = cached?.takeIf { it.accountKey == key }?.entries.orEmpty()
            .filterValues { entry -> entry.programmes.any { it.stop + offsetMillis > now } }
        return CachedShortGuide(key, entries)
    }

    suspend fun saveShortGuide(credentials: PanelCredentials, channelId: String, entry: CachedShortGuideEntry, offsetMillis: Long = 0) {
        val token = repository.sessionToken(credentials) ?: return
        val key = accountKey(credentials)
        fileMutex.withLock {
            if (!repository.isSessionCurrent(credentials, token)) return@withLock
            val previous = read<CachedShortGuide>(file(key, "short-epg"))?.takeIf { it.accountKey == key }
            val now = System.currentTimeMillis()
            val entries = (previous?.entries.orEmpty() + (channelId to entry)).entries
                .filter { it.value.programmes.any { programme -> programme.stop + offsetMillis > now } }
                .sortedByDescending { it.value.fetchedAtMillis }.take(MAX_SHORT_EPG_CHANNELS)
                .associate { it.key to it.value }
            write(file(key, "short-epg"), CachedShortGuide(key, entries))
        }
    }

    suspend fun refresh(
        credentials: PanelCredentials,
        force: Boolean = false,
        onProgress: (CatalogStage) -> Unit = {},
    ): CatalogRefreshResult = refreshMutex.withLock {
        val token = repository.sessionToken(credentials) ?: return@withLock CatalogRefreshResult(null, 1, false)
        val previous = load(credentials)
        if (!repository.isSessionCurrent(credentials, token)) return@withLock CatalogRefreshResult(null, 1, false)
        if (!force && previous?.isFresh(intervalDays = repository.settings.value.catalogIntervalDays) == true)
            return@withLock CatalogRefreshResult(previous, 0, false)
        val result = coroutineScope {
            onProgress(CatalogStage.PLAYLIST)
            // Start the independent requests together, but advance the label only as each
            // actual section is awaited. Fast panels never see an artificial delay.
            val lc = async { repository.liveCategories() }
            val ch = async { repository.liveChannels() }
            val mc = async { repository.vodCategories() }
            val mv = async { repository.vodItems() }
            val sc = async { repository.seriesCategories() }
            val se = async { repository.series() }
            val liveCats = lc.await(); val movieCats = mc.await(); val seriesCats = sc.await()
            onProgress(CatalogStage.CHANNELS)
            val channels = ch.await()
            onProgress(CatalogStage.MOVIES)
            val movies = mv.await()
            onProgress(CatalogStage.SERIES)
            val series = se.await()
            val failures = listOf(liveCats, channels, movieCats, movies, seriesCats, series).count { it is ApiResult.Failure }
            val liveOk = liveCats is ApiResult.Success && channels is ApiResult.Success
            val moviesOk = movieCats is ApiResult.Success && movies is ApiResult.Success
            val seriesOk = seriesCats is ApiResult.Success && series is ApiResult.Success
            val next = CachedCatalog(
                accountKey = accountKey(credentials),
                updatedAtMillis = if (failures == 0) System.currentTimeMillis() else previous?.updatedAtMillis ?: 0,
                complete = failures == 0,
                liveCategories = if (liveOk) liveCats.getOrNull().orEmpty() else previous?.liveCategories.orEmpty(),
                channels = if (liveOk) channels.getOrNull().orEmpty() else previous?.channels.orEmpty(),
                movieCategories = if (moviesOk) movieCats.getOrNull().orEmpty() else previous?.movieCategories.orEmpty(),
                movies = if (moviesOk) movies.getOrNull().orEmpty() else previous?.movies.orEmpty(),
                seriesCategories = if (seriesOk) seriesCats.getOrNull().orEmpty() else previous?.seriesCategories.orEmpty(),
                series = if (seriesOk) series.getOrNull().orEmpty() else previous?.series.orEmpty(),
            )
            CatalogRefreshResult(if (previous != null || liveOk || moviesOk || seriesOk) next else null, failures, true)
        }
        if (!repository.isSessionCurrent(credentials, token)) return@withLock CatalogRefreshResult(null, 1, true)
        val next = result.snapshot ?: return@withLock result
        val saved = fileMutex.withLock {
            if (!repository.isSessionCurrent(credentials, token)) false else {
                val persisted = write(file(next.accountKey, "catalog"), next)
                if (repository.isSessionCurrent(credentials, token)) _catalog.value = next
                persisted
            }
        }
        // Still show the new data in memory if disk is full, but report it and leave the old file intact.
        if (!saved) result.copy(failures = result.failures + 1) else result
    }

    /** Clear only the active account's local snapshots; favourites and credentials are untouched. */
    suspend fun clearActive(credentials: PanelCredentials): Boolean {
        val token = repository.sessionToken(credentials) ?: return false
        val key = accountKey(credentials)
        return refreshMutex.withLock {
            fileMutex.withLock files@{
                if (!repository.isSessionCurrent(credentials, token)) return@files false
                withContext(Dispatchers.IO) {
                    listOf("catalog", "heroes", "short-epg").forEach { AtomicFile(file(key, it)).delete() }
                }
                if (!repository.isSessionCurrent(credentials, token)) return@files false
                _catalog.value = null
                _heroes.value = null
                true
            }
        }
    }

    /** Drop memory immediately, and delete just this account's files after in-flight disk writes. */
    fun forgetMemory() {
        _catalog.value = null
        _heroes.value = null
    }

    suspend fun deleteAccount(credentials: PanelCredentials) {
        val key = accountKey(credentials)
        fileMutex.withLock {
            // A delayed logout cleanup must not delete a cache just reused by a new login.
            if (repository.currentCredentials()?.let { accountKey(it) } == key) return@withLock
            withContext(Dispatchers.IO) {
                listOf("catalog", "heroes", "short-epg").forEach { AtomicFile(file(key, it)).delete() }
            }
        }
    }

    private fun file(key: String, kind: String) = File(directory, "$key-$kind.json.gz")

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> read(file: File): T? = withContext(Dispatchers.IO) {
        if ((!file.isFile && !File(file.path + ".bak").isFile) || file.length() > MAX_CACHE_BYTES) return@withContext null
        try {
            GZIPInputStream(AtomicFile(file).openRead()).use { ApiClient.json.decodeFromStream<T>(it) }
        } catch (_: Exception) {
            null // corrupt/evicted cache: retain the session and fall back to network
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> write(file: File, value: T): Boolean = withContext(Dispatchers.IO) {
        val atomic = AtomicFile(file)
        var output: java.io.FileOutputStream? = null
        try {
            directory.mkdirs()
            output = atomic.startWrite()
            val gzip = GZIPOutputStream(LimitedOutputStream(output, MAX_CACHE_BYTES))
            ApiClient.json.encodeToStream(value, gzip)
            gzip.finish()
            gzip.flush()
            atomic.finishWrite(output)
            true
        } catch (_: Exception) {
            if (output != null) atomic.failWrite(output)
            false
        }
    }

    companion object {
        private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
        private const val MAX_HERO_ENTRIES = 60
        private const val MAX_SHORT_EPG_CHANNELS = 2000

        fun accountKey(credentials: PanelCredentials): String {
            val input = "${PanelUrl.normalize(credentials.serverUrl)}\u0000${credentials.username}"
            return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}

private class LimitedOutputStream(output: OutputStream, private val limit: Long) : FilterOutputStream(output) {
    private var written = 0L
    override fun write(value: Int) {
        if (++written > limit) throw IOException("Cache size limit exceeded")
        out.write(value)
    }
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        written += length
        if (written > limit) throw IOException("Cache size limit exceeded")
        out.write(buffer, offset, length)
    }
}
