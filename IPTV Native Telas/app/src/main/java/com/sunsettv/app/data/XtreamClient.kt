package com.sunsettv.app.data

import android.net.Uri
import android.util.Base64
import com.sunsettv.app.domain.AccountInfo
import com.sunsettv.app.domain.EpgProgram
import com.sunsettv.app.domain.LiveCategory
import com.sunsettv.app.domain.LiveChannel
import com.sunsettv.app.domain.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

private const val UA = "SunsetTV/1.0"

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** Cliente da Xtream Codes API (player_api.php + xmltv.php). */
class XtreamClient(private val http: OkHttpClient) {

    private fun apiUrl(c: XtreamCredentials, action: String? = null, vararg extra: Pair<String, String>): HttpUrl {
        val b = "${c.baseUrl}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
        action?.let { b.addQueryParameter("action", it) }
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private suspend fun fetch(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Servidor respondeu HTTP ${r.code}")
            r.body?.string() ?: throw IOException("Resposta vazia do servidor")
        }
    }

    fun liveUrl(c: XtreamCredentials, streamId: Int): String =
        "${c.baseUrl}/live/${Uri.encode(c.username)}/${Uri.encode(c.password)}/$streamId.m3u8"

    fun xmltvUrl(c: XtreamCredentials): String =
        "${c.baseUrl}/xmltv.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
            .build().toString()

    suspend fun getAccountInfo(c: XtreamCredentials): AccountInfo {
        val raw = fetch(apiUrl(c))
        return withContext(Dispatchers.Default) {
            val root = JSONObject(raw)
            val u = root.optJSONObject("user_info") ?: throw IOException("Credenciais inválidas")
            if (u.str("auth") == "0") throw IOException("Usuário ou senha inválidos")
            val s = root.optJSONObject("server_info")
            AccountInfo(
                username = u.str("username") ?: c.username,
                status = u.str("status") ?: "Unknown",
                expiresAtMs = u.str("exp_date")?.toLongOrNull()?.times(1000),
                createdAtMs = u.str("created_at")?.toLongOrNull()?.times(1000),
                isTrial = u.str("is_trial") == "1",
                activeConnections = u.str("active_cons")?.toIntOrNull() ?: 0,
                maxConnections = u.str("max_connections")?.toIntOrNull() ?: 0,
                serverUrl = c.baseUrl,
                timezone = s?.str("timezone"),
            )
        }
    }

    suspend fun getLiveCategories(c: XtreamCredentials): List<LiveCategory> {
        val raw = fetch(apiUrl(c, "get_live_categories"))
        return withContext(Dispatchers.Default) {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.str("category_id") ?: continue
                    add(LiveCategory(id, o.str("category_name") ?: "Categoria $id"))
                }
            }
        }
    }

    suspend fun getLiveChannels(c: XtreamCredentials): List<LiveChannel> {
        val raw = fetch(apiUrl(c, "get_live_streams"))
        return withContext(Dispatchers.Default) {
            val arr = JSONArray(raw)
            val out = ArrayList<LiveChannel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("stream_id", -1)
                if (id < 0) continue
                out += LiveChannel(
                    streamId = id,
                    number = o.optInt("num", i + 1),
                    name = o.str("name") ?: "Canal $id",
                    logoUrl = o.str("stream_icon"),
                    categoryId = o.str("category_id") ?: "0",
                    epgChannelId = o.str("epg_channel_id"),
                    streamUrl = liveUrl(c, id),
                )
            }
            out
        }
    }

    /** EPG curto (usado como fallback quando o XMLTV não tem o canal). */
    suspend fun getShortEpg(c: XtreamCredentials, streamId: Int, limit: Int = 6): List<EpgProgram> {
        val raw = fetch(apiUrl(c, "get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString()))
        return withContext(Dispatchers.Default) {
            val arr = JSONObject(raw).optJSONArray("epg_listings") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val start = o.str("start_timestamp")?.toLongOrNull() ?: continue
                    val end = o.str("stop_timestamp")?.toLongOrNull() ?: continue
                    add(
                        EpgProgram(
                            title = decodeB64(o.str("title")).ifBlank { "Sem título" },
                            description = decodeB64(o.str("description")),
                            startMs = start * 1000,
                            endMs = end * 1000,
                        )
                    )
                }
            }.sortedBy { it.startMs }
        }
    }

    /** Baixa um arquivo grande (XMLTV) em streaming, reportando progresso 0..1 quando o tamanho é conhecido. */
    suspend fun download(url: String, target: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Servidor respondeu HTTP ${r.code}")
            val body = r.body ?: throw IOException("Resposta vazia do servidor")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target.delete()
        if (!tmp.renameTo(target)) throw IOException("Não foi possível salvar o arquivo do EPG")
    }

    private fun decodeB64(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (_: IllegalArgumentException) {
            s
        }
    }
}
