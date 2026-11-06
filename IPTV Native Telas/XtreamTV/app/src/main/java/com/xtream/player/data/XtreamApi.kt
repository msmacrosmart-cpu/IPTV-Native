package com.xtream.player.data

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object XtreamApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private const val UA = "VLC/3.0.20 LibVLC/3.0.20"

    fun normalize(server: String): String {
        var s = server.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s.trimEnd('/')
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String) = http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute()

    private fun fetch(url: String): String = open(url).use { r ->
        if (!r.isSuccessful) error("Servidor respondeu HTTP ${r.code}")
        r.body?.string() ?: error("Resposta vazia")
    }

    private fun api(s: Session, action: String? = null, extra: String = ""): String {
        val b = normalize(s.server)
        val a = if (action != null) "&action=$action" else ""
        return fetch("$b/player_api.php?username=${enc(s.username)}&password=${enc(s.password)}$a$extra")
    }

    fun authenticate(server: String, user: String, pass: String): Session {
        val tmp = Session(server, user, pass, "", null, null, 0, 0)
        val json = JSONObject(api(tmp))
        val ui = json.optJSONObject("user_info") ?: error("Resposta inválida do servidor")
        if (ui.optInt("auth", 0) != 1) error("Usuário ou senha inválidos")
        return Session(
            server = server.trim(), username = user, password = pass,
            status = ui.optString("status", "Active"),
            expDate = ui.optString("exp_date").toLongOrNull()?.times(1000),
            createdAt = ui.optString("created_at").toLongOrNull()?.times(1000),
            activeConns = ui.optString("active_cons").toIntOrNull() ?: 0,
            maxConns = ui.optString("max_connections").toIntOrNull() ?: 0,
        )
    }

    private fun categories(s: Session, action: String): List<Category> {
        val arr = JSONArray(api(s, action))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { Category(it.optString("category_id"), it.optString("category_name")) }
    }

    fun loadContent(s: Session): ContentState {
        val b = normalize(s.server)
        val liveCats = categories(s, "get_live_categories")
        val vodCats = categories(s, "get_vod_categories")
        val serCats = categories(s, "get_series_categories")

        val liveArr = JSONArray(api(s, "get_live_streams"))
        val channels = (0 until liveArr.length()).map { liveArr.getJSONObject(it) }.mapIndexed { i, o ->
            val id = o.optString("stream_id")
            Channel(
                id = id, number = o.optInt("num", i + 1), name = o.optString("name"),
                logo = o.optString("stream_icon").ifBlank { null }, categoryId = o.optString("category_id"),
                epgId = o.optString("epg_channel_id").ifBlank { null },
                url = "$b/live/${enc(s.username)}/${enc(s.password)}/$id.m3u8",
            )
        }

        val vodArr = JSONArray(api(s, "get_vod_streams"))
        val movies = (0 until vodArr.length()).map { vodArr.getJSONObject(it) }.map { o ->
            val id = o.optString("stream_id")
            val ext = o.optString("container_extension").ifBlank { "mp4" }
            VodItem(
                id = id, title = o.optString("name"), year = o.optString("year"),
                rating = o.optString("rating").toDoubleOrNull() ?: 0.0, genre = "",
                plot = "", poster = o.optString("stream_icon").ifBlank { null }, backdrop = null,
                categoryId = o.optString("category_id"),
                url = "$b/movie/${enc(s.username)}/${enc(s.password)}/$id.$ext",
            )
        }

        val serArr = JSONArray(api(s, "get_series"))
        val series = (0 until serArr.length()).map { serArr.getJSONObject(it) }.map { o ->
            val id = o.optString("series_id")
            VodItem(
                id = id, title = o.optString("name"), year = o.optString("releaseDate").take(4),
                rating = o.optString("rating").toDoubleOrNull() ?: 0.0,
                genre = o.optString("genre"), plot = o.optString("plot"),
                poster = o.optString("cover").ifBlank { null },
                backdrop = o.optJSONArray("backdrop_path")?.optString(0)?.ifBlank { null },
                categoryId = o.optString("category_id"), url = null, seriesId = id,
            )
        }
        val hero = movies.filter { it.poster != null }.maxByOrNull { it.rating }
        return ContentState(liveCats, channels, vodCats, movies, serCats, series, hero)
    }

    /** Lista de episódios (título, url) de uma série. */
    fun seriesEpisodes(s: Session, seriesId: String): List<Pair<String, String>> {
        val b = normalize(s.server)
        val eps = JSONObject(api(s, "get_series_info", "&series_id=$seriesId")).optJSONObject("episodes")
            ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        eps.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }.forEach { season ->
            val arr = eps.optJSONArray(season) ?: return@forEach
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val ext = e.optString("container_extension").ifBlank { "mp4" }
                out += "T$season E${e.optString("episode_num")} · ${e.optString("title")}" to
                    "$b/series/${enc(s.username)}/${enc(s.password)}/${e.optString("id")}.$ext"
            }
        }
        return out
    }

    fun fetchEpg(s: Session): Map<String, List<Programme>> {
        val b = normalize(s.server)
        return open("$b/xmltv.php?username=${enc(s.username)}&password=${enc(s.password)}").use { r ->
            if (!r.isSuccessful) error("EPG HTTP ${r.code}")
            parseXmltv(r.body!!.byteStream())
        }
    }

    fun parseXmltv(input: InputStream): Map<String, List<Programme>> {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val now = System.currentTimeMillis()
        val from = now - 3 * 3_600_000L
        val to = now + 36 * 3_600_000L
        val map = HashMap<String, MutableList<Programme>>()
        val p = Xml.newPullParser()
        p.setInput(input, null)
        var ch: String? = null; var st = 0L; var en = 0L
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "programme" -> {
                        ch = p.getAttributeValue(null, "channel")
                        st = runCatching { fmt.parse(p.getAttributeValue(null, "start"))!!.time }.getOrDefault(0L)
                        en = runCatching { fmt.parse(p.getAttributeValue(null, "stop"))!!.time }.getOrDefault(0L)
                    }
                    "title" -> {
                        val t = p.nextText()
                        val c = ch
                        if (c != null && en > from && st < to) map.getOrPut(c) { mutableListOf() } += Programme(c, t, st, en)
                    }
                }
            }
            ev = p.next()
        }
        return map
    }
}
