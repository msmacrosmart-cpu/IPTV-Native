package com.example.iptv.data.tmdb

import com.example.iptv.data.model.VodDetails
import com.example.iptv.data.network.ApiClient
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/** A non-clickable cast entry. Photo URLs, when present, come only from TMDB's image host. */
data class CastMember(val name: String, val character: String? = null, val photo: String? = null)

data class DetailExtras(
    val cast: List<CastMember> = emptyList(),
    val youtubeId: String? = null,
    val fromTmdb: Boolean = false,
)

data class TmdbMetadata(val details: VodDetails, val extras: DetailExtras)

sealed interface TmdbResult {
    data class Found(val metadata: TmdbMetadata) : TmdbResult
    data object NotFound : TmdbResult
    data object Unauthorized : TmdbResult
    data object Unavailable : TmdbResult
}

/** Accept a video ID or an official YouTube URL, never an arbitrary provider-supplied URL. */
fun youtubeVideoId(value: String?): String? {
    val raw = value?.trim().orEmpty()
    val pattern = Regex("^[A-Za-z0-9_-]{11}$")
    if (pattern.matches(raw)) return raw
    val url = raw.toHttpUrlOrNull() ?: return null
    val host = url.host.lowercase(Locale.ROOT)
    val id = when {
        host == "youtu.be" -> url.pathSegments.firstOrNull()
        host == "youtube.com" || host.endsWith(".youtube.com") ->
            url.queryParameter("v") ?: url.pathSegments.takeIf { it.firstOrNull() in setOf("embed", "shorts", "live") }?.getOrNull(1)
        else -> null
    }
    return id?.takeIf(pattern::matches)
}

fun providerExtras(details: VodDetails?): DetailExtras = DetailExtras(
    cast = details?.cast.orEmpty().split(',', ';').map(String::trim).filter(String::isNotEmpty)
        .map { CastMember(it) },
    youtubeId = youtubeVideoId(details?.youtubeTrailer),
)

/**
 * TMDB v3 lookups happen only when opening details and only after a key is configured.
 * No credentials from the IPTV account are ever sent to TMDB. The base URL is injectable
 * for tests; production uses HTTPS. Requests are cancellable and responses are bounded.
 */
class TmdbRepository(
    client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl(),
    private val imageBaseUrl: HttpUrl = "https://image.tmdb.org/t/p/".toHttpUrl(),
) {
    // The v3 API key is a query parameter: never follow a redirect to another host or HTTP.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun lookup(title: String, year: String, isSeries: Boolean, providerId: String?, apiKey: String): TmdbResult {
        if (apiKey.isBlank()) return TmdbResult.Unavailable
        val type = if (isSeries) "tv" else "movie"
        var id = providerId?.toLongOrNull()?.takeIf { it > 0 }
        if (id == null) {
            val query = cleanTitle(title)
            if (query.isBlank()) return TmdbResult.NotFound
            val validYear = year.takeIf { it.length == 4 && it.toIntOrNull()?.let { number -> number in 1900..2100 } == true }
            val first = search(type, query, validYear, apiKey)
            if (first.status == 401 || first.status == 403) return TmdbResult.Unauthorized
            if (first.status !in 200..299 || first.body == null) return TmdbResult.Unavailable
            id = findMatch(first.body, query)
            if (id == null && validYear != null) {
                val withoutYear = search(type, query, null, apiKey)
                if (withoutYear.status == 401 || withoutYear.status == 403) return TmdbResult.Unauthorized
                if (withoutYear.status !in 200..299 || withoutYear.body == null) return TmdbResult.Unavailable
                id = findMatch(withoutYear.body, query)
            }
        }
        id ?: return TmdbResult.NotFound
        val url = baseUrl.newBuilder().addPathSegment(type).addPathSegment(id.toString())
            .addQueryParameter("api_key", apiKey).addQueryParameter("language", "pt-BR")
            .addQueryParameter("append_to_response", "videos,credits")
            .addQueryParameter("include_video_language", "pt,en,null").build()
        val response = get(url)
        return when {
            response.status == 401 || response.status == 403 -> TmdbResult.Unauthorized
            response.status == 404 -> TmdbResult.NotFound
            response.status !in 200..299 || response.body == null -> TmdbResult.Unavailable
            else -> parseMetadata(response.body)?.let { TmdbResult.Found(it) } ?: TmdbResult.Unavailable
        }
    }

    private suspend fun search(type: String, title: String, year: String?, key: String): HttpResult {
        val url = baseUrl.newBuilder().addPathSegment("search").addPathSegment(type)
            .addQueryParameter("api_key", key).addQueryParameter("language", "pt-BR")
            .addQueryParameter("query", title).addQueryParameter("include_adult", "false")
            .apply { if (year != null) addQueryParameter(if (type == "tv") "first_air_date_year" else "year", year) }.build()
        return get(url)
    }

    private fun findMatch(body: String, query: String): Long? {
        val results = (parse(body)?.get("results") as? JsonArray).orEmpty()
        val expected = normalized(query)
        if (expected.isBlank()) return null
        // A strict title match avoids displaying the cast/trailer of a different film.
        return results.asSequence().mapNotNull { it as? JsonObject }.firstOrNull { result ->
            listOf("title", "original_title", "name", "original_name").any { normalized(result.text(it).orEmpty()) == expected }
        }?.text("id")?.toLongOrNull()?.takeIf { it > 0 }
    }

    private fun parseMetadata(body: String): TmdbMetadata? {
        val root = parse(body) ?: return null
        val credits = root["credits"] as? JsonObject
        val cast = (credits?.get("cast") as? JsonArray).orEmpty().mapNotNull { entry ->
            val person = entry as? JsonObject ?: return@mapNotNull null
            val name = person.text("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            CastMember(name, person.text("character")?.takeIf(String::isNotBlank), imageUrl(person.text("profile_path"), "w185"))
        }
        val videos = (root["videos"] as? JsonObject)?.get("results") as? JsonArray
        val trailer = videos.orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.text("site").equals("YouTube", true) && it.text("type").equals("Trailer", true) }
            .sortedByDescending { it.text("official") == "true" }
            .firstNotNullOfOrNull { youtubeVideoId(it.text("key")) }
        val genres = (root["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("name") }.joinToString(", ")
        val details = VodDetails(
            plot = root.text("overview")?.takeIf(String::isNotBlank),
            genre = genres.takeIf(String::isNotBlank),
            releaseDate = root.text("release_date") ?: root.text("first_air_date"),
            backdrop = imageUrl(root.text("backdrop_path"), "w1280"),
            rating = root.text("vote_average")?.takeIf { it.toDoubleOrNull()?.let { rating -> rating > 0 } == true },
        )
        return TmdbMetadata(details, DetailExtras(cast, trailer, fromTmdb = true))
    }

    private fun imageUrl(path: String?, size: String): String? = path
        ?.takeIf { it.startsWith('/') && !it.startsWith("//") && ".." !in it && '?' !in it && '#' !in it }
        ?.let { imageBaseUrl.newBuilder().addPathSegment(size).addPathSegments(it.removePrefix("/")).build().toString() }

    private fun parse(body: String): JsonObject? = runCatching { ApiClient.json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    private data class HttpResult(val status: Int, val body: String?)

    private suspend fun get(url: HttpUrl): HttpResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(HttpResult(0, null))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = try {
                        val body = if (it.isSuccessful) {
                            val source = it.body?.source()
                            if (source == null) null else {
                                val buffer = Buffer()
                                while (true) {
                                    val read = source.read(buffer, 8192)
                                    if (read == -1L) break
                                    if (buffer.size > MAX_RESPONSE_BYTES) throw IOException("TMDB response too large")
                                }
                                buffer.readUtf8()
                            }
                        } else null
                        HttpResult(it.code, body)
                    } catch (_: IOException) { HttpResult(0, null) }
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

        internal fun cleanTitle(title: String): String = title
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\((?:19|20)\\d{2}\\)"), " ")
            .replace(Regex("(?i)\\b(?:4k|fhd|uhd|1080p|720p|dublado|legendado)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()

        private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    }
}
