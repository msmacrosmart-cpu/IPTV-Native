package com.xtream.player.data

import android.content.Context
import com.example.iptv.data.model.PanelCredentials
import org.json.JSONObject
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Favorites and playback positions are private to each panel/account and survive app restarts. */
class UserLibrary(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("xtream_library", Context.MODE_PRIVATE)
    private var account = ""

    data class Progress(val position: Long, val duration: Long, val episodeId: String?) {
        val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    fun select(credentials: PanelCredentials?) {
        val identity = credentials?.let {
            // HttpUrl canonicalizes scheme/host without lowercasing a case-sensitive panel path.
            val server = it.serverUrl.toHttpUrlOrNull()?.toString()?.trimEnd('/') ?: it.serverUrl.trimEnd('/')
            "$server\u0000${it.username}"
        } ?: "demo"
        account = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun favorites(): Set<String> = prefs.getStringSet("$account.favorites", emptySet()).orEmpty().toSet()
    fun saveFavorites(values: Collection<String>) {
        prefs.edit().putStringSet("$account.favorites", values.toSet()).apply()
    }

    fun progress(): Map<String, Progress> {
        val prefix = "$account.progress."
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || value !is String) return@mapNotNull null
            runCatching {
                val json = JSONObject(value)
                key.removePrefix(prefix) to Progress(
                    json.getLong("position"), json.getLong("duration"),
                    json.optString("episode").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
        }.toMap()
    }

    fun saveProgress(key: String, position: Long, duration: Long, episodeId: String?): Progress? {
        val prefKey = "$account.progress.$key"
        // Do not add a film to Continue Watching for an accidental click or after it finishes.
        if (duration <= 0 || position < 5_000 || position >= duration * 0.95) {
            prefs.edit().remove(prefKey).apply()
            return null
        }
        val progress = Progress(position, duration, episodeId)
        val json = JSONObject().put("position", position).put("duration", duration)
        if (episodeId != null) json.put("episode", episodeId)
        prefs.edit().putString(prefKey, json.toString()).apply()
        return progress
    }
}
