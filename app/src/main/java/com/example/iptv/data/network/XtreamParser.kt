package com.example.iptv.data.network

import android.util.Base64
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.Episode
import com.example.iptv.data.model.EpgEntry
import com.example.iptv.data.model.LiveCategory
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.SeasonEpisodes
import com.example.iptv.data.model.SeriesCategory
import com.example.iptv.data.model.SeriesItem
import com.example.iptv.data.model.VodCategory
import com.example.iptv.data.model.VodDetails
import com.example.iptv.data.model.VodItem
import com.example.iptv.util.FormatUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Defensive parser for Xtream Codes payloads.
 *
 * Every field is read as `number-or-string` and every list tolerates objects, empty objects
 * or nulls. This is what prevents "empty grid / black screen" behaviour on panels that do not
 * follow the reference implementation byte-for-byte.
 */
object XtreamParser {

    // ------------------------------------------------------------------ account

    fun parseAccount(root: JsonElement, panelUrl: String): AccountInfo {
        val obj = root.asObject()
        val user = obj?.get("user_info").asObject()
        val server = obj?.get("server_info").asObject()

        val status = user?.string("status") ?: "Unknown"
        val auth = user?.int("auth")
        // Some panels omit auth or status, but an explicit rejection/disabled status wins.
        val isActive = auth != 0 && when {
            status.equals("Unknown", true) -> auth == 1
            else -> status.equals("Active", true)
        }

        val allowedFormats = user?.stringList("allowed_output_formats") ?: emptyList()

        return AccountInfo(
            username = user?.string("username") ?: "",
            status = status,
            message = user?.string("message") ?: "",
            isActive = isActive,
            isTrial = user?.bool("is_trial") ?: false,
            expiresAtMillis = epochMillis(user?.long("exp_date")),
            createdAtMillis = epochMillis(user?.long("created_at")),
            maxConnections = user?.int("max_connections") ?: 1,
            activeConnections = user?.int("active_cons") ?: 0,
            allowedFormats = allowedFormats,
            serverProtocol = server?.string("server_protocol") ?: "http",
            serverPort = server?.string("port").orEmpty(),
            timezone = server?.string("timezone") ?: "UTC",
            panelUrl = panelUrl
        )
    }

    // Xtream usually sends Unix seconds; accept milliseconds from panels that already convert it.
    private fun epochMillis(value: Long?): Long? = value?.takeIf { it > 0L }?.let {
        if (it < 10_000_000_000L) it * 1000L else it
    }

    // ---------------------------------------------------------------- categories

    fun parseLiveCategories(root: JsonElement): List<LiveCategory> =
        root.asList()?.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            val name = obj.string("category_name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LiveCategory(id = id, name = name)
        }?.distinctBy { it.id }.orEmpty()

    fun parseVodCategories(root: JsonElement): List<VodCategory> =
        root.asList()?.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            val name = obj.string("category_name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VodCategory(id = id, name = name)
        }?.distinctBy { it.id }.orEmpty()

    fun parseSeriesCategories(root: JsonElement): List<SeriesCategory> =
        root.asList()?.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            val name = obj.string("category_name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SeriesCategory(id = id, name = name)
        }?.distinctBy { it.id }.orEmpty()

    // -------------------------------------------------------------- live streams

    fun parseLiveChannels(root: JsonElement): List<LiveChannel> =
        root.asList()?.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.int("stream_id") ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LiveChannel(
                id = id,
                name = name,
                streamNumber = obj.int("num") ?: 0,
                icon = obj.string("stream_icon")?.takeIf { it.isNotBlank() },
                epgChannelId = obj.string("epg_channel_id")?.takeIf { it.isNotBlank() },
                categoryId = obj.string("category_id")?.takeIf { it.isNotBlank() },
                hasArchive = (obj.int("tv_archive") ?: 0) > 0
            )
        }?.distinctBy { it.id }.orEmpty()

    // --------------------------------------------------------------- vod streams

    fun parseVodStreams(root: JsonElement): List<VodItem> =
        root.asList()?.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.int("stream_id") ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VodItem(
                id = id,
                name = name,
                icon = obj.string("stream_icon")?.takeIf { it.isNotBlank() },
                rating = obj.string("rating")?.takeIf { it.isNotBlank() && it != "0" },
                rating5 = obj.double("rating_5based")?.takeIf { it > 0.0 },
                extension = obj.string("container_extension")?.trim().orEmpty().ifBlank { "mp4" },
                categoryId = obj.string("category_id")?.takeIf { it.isNotBlank() },
                plot = obj.string("plot")?.takeIf { it.isNotBlank() },
                addedAtMillis = epochMillis(obj.long("added"))
            )
        }?.distinctBy { it.id }.orEmpty()

    // ---------------------------------------------------------------- series list

    fun parseSeries(root: JsonElement): List<SeriesItem> =
        root.asList()?.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val id = obj.int("series_id") ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val backdropPath = obj.string("backdrop_path") ?: obj.stringList("backdrop_path")?.firstOrNull()
            SeriesItem(
                id = id,
                name = name,
                cover = obj.string("cover")?.takeIf { it.isNotBlank() },
                plot = obj.string("plot")?.takeIf { it.isNotBlank() },
                cast = obj.string("cast")?.takeIf { it.isNotBlank() },
                genre = obj.string("genre")?.takeIf { it.isNotBlank() },
                releaseDate = obj.string("releaseDate") ?: obj.string("release_date"),
                rating = obj.string("rating")?.takeIf { it.isNotBlank() && it != "0" },
                rating5 = obj.double("rating_5based")?.takeIf { it > 0.0 },
                backdrop = backdropPath?.takeIf { it.isNotBlank() },
                categoryId = obj.string("category_id")?.takeIf { it.isNotBlank() }
            )
        }?.distinctBy { it.id }.orEmpty()

    // --------------------------------------------------------------------- EPG

    fun parseShortEpg(root: JsonElement): List<EpgEntry> {
        val listings = root.asObject()?.get("epg_listings").asList() ?: return emptyList()
        val parsed = listings.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val title = decodeText(obj.string("title")).ifBlank { return@mapNotNull null }
            val description = decodeText(obj.string("description"))

            val start = obj.long("start_timestamp")?.times(1000L)
                ?: FormatUtils.parseEpgDate(obj.string("start"))
            val end = obj.long("stop_timestamp")?.times(1000L)
                ?: FormatUtils.parseEpgDate(obj.string("end"))

            val startMillis = start ?: return@mapNotNull null
            val endMillis = end?.takeIf { it > startMillis } ?: (startMillis + 30 * 60 * 1000L)

            EpgEntry(
                id = obj.string("id") ?: obj.string("epg_id") ?: title,
                title = title,
                description = description,
                startMillis = startMillis,
                endMillis = endMillis,
                isNowPlaying = (obj.int("now_playing") ?: 0) == 1
            )
        }
        return parsed.distinctBy { it.id + it.startMillis }.sortedBy { it.startMillis }
    }

    fun parseFullEpg(root: JsonElement): List<EpgEntry> = parseShortEpg(root)

    // ------------------------------------------------------------- series detail

    fun parseSeriesInfo(root: JsonElement): List<SeasonEpisodes> {
        val rootObj = root.asObject() ?: return emptyList()
        val episodesElement = rootObj["episodes"] ?: return emptyList()
        val seasons = LinkedHashMap<Int, MutableList<Episode>>()

        when (episodesElement) {
            is JsonArray -> {
                // Some panels return a flat array for single-season shows.
                episodesElement.forEach { item ->
                    val obj = item.asObject() ?: return@forEach
                    val episode = parseEpisode(obj, fallbackSeason = 1) ?: return@forEach
                    seasons.getOrPut(episode.season) { mutableListOf() }.add(episode)
                }
            }
            is JsonObject -> {
                for (entry in episodesElement.entries) {
                    val seasonNumber = entry.key.toIntOrNull() ?: 1
                    val items = entry.value.asList() ?: continue
                    for (item in items) {
                        val obj = item.asObject() ?: continue
                        val episode = parseEpisode(obj, fallbackSeason = seasonNumber) ?: continue
                        seasons.getOrPut(episode.season) { mutableListOf() }.add(episode)
                    }
                }
            }
            else -> Unit
        }

        return seasons.entries
            .sortedBy { it.key }
            .map { entry ->
                SeasonEpisodes(
                    season = entry.key,
                    episodes = entry.value
                        .sortedWith(compareBy({ it.number }, { it.id }))
                        .distinctBy { it.id }
                )
            }
            .filter { it.episodes.isNotEmpty() }
    }

    fun parseSeriesEpisodeCount(root: JsonElement): Int {
        val seasons = parseSeriesInfo(root)
        return seasons.sumOf { it.episodes.size }
    }

    private fun parseEpisode(obj: JsonObject, fallbackSeason: Int): Episode? {
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val info = obj["info"].asObject()
        val number = obj.int("episode_num") ?: 0
        val title = obj.string("title")?.takeIf { it.isNotBlank() }
            ?: "Episódio $number"
        return Episode(
            id = id,
            title = title,
            season = obj.int("season") ?: fallbackSeason,
            number = number,
            extension = obj.string("container_extension")?.trim().orEmpty().ifBlank { "mp4" },
            plot = info?.string("plot")?.takeIf { it.isNotBlank() },
            durationLabel = info?.string("duration")?.takeIf { it.isNotBlank() },
            image = info?.string("movie_image")?.takeIf { it.isNotBlank() }
        )
    }

    // ----------------------------------------------------------------- vod info

    fun parseVodInfo(root: JsonElement): VodDetails {
        val info = root.asObject()?.get("info").asObject()
        val backdrop = info?.string("backdrop_path") ?: info?.stringList("backdrop_path")?.firstOrNull()
        return VodDetails(
            plot = info?.string("plot")?.takeIf { it.isNotBlank() },
            cast = info?.string("cast")?.takeIf { it.isNotBlank() },
            director = info?.string("director")?.takeIf { it.isNotBlank() },
            genre = info?.string("genre")?.takeIf { it.isNotBlank() },
            releaseDate = info?.string("releasedate") ?: info?.string("release_date") ?: info?.string("releaseDate"),
            durationLabel = info?.string("duration")?.takeIf { it.isNotBlank() },
            backdrop = backdrop?.takeIf { it.isNotBlank() },
            rating = info?.string("rating")?.takeIf { it.isNotBlank() }
        )
    }

    // -------------------------------------------------------------- json helpers

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asList(): List<JsonElement>? = this as? JsonArray

    private fun JsonObject.string(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return when (element) {
            is JsonPrimitive -> element.content.takeIf { it.isNotBlank() && it != "null" }
            is JsonArray -> element.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
            else -> null
        }
    }

    private fun JsonObject.int(key: String): Int? {
        val raw = string(key) ?: return null
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.long(key: String): Long? {
        val raw = string(key) ?: return null
        return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
    }

    private fun JsonObject.double(key: String): Double? {
        val raw = string(key) ?: return null
        return raw.toDoubleOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val raw = string(key) ?: return null
        return when (raw.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }

    private fun JsonObject.stringList(key: String): List<String>? {
        val element = this[key] ?: return null
        return when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() } }
            is JsonPrimitive -> listOf(element.content).filter { it.isNotBlank() }
            else -> null
        }
    }

    /**
     * Xtream sends EPG titles/descriptions base64 encoded. A few panels do not, so the value
     * is only decoded when it really looks like base64 and decodes to printable text.
     */
    fun decodeText(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""

        val compact = value.replace("\n", "").replace("\r", "").replace(" ", "")
        val looksBase64 = compact.length % 4 == 0 &&
            compact.length >= 8 &&
            compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        if (!looksBase64) return value

        val decoded = try {
            String(Base64.decode(compact, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Throwable) {
            return value
        }

        val printable = decoded.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,:;!?-()[]'\"%/&+áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ" }
        val ratio = if (decoded.isEmpty()) 0.0 else printable.toDouble() / decoded.length
        return if (decoded.isNotBlank() && ratio > 0.85) decoded.trim() else value
    }
}
