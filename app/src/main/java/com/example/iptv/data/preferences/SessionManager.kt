package com.example.iptv.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.PlaybackApp
import com.example.iptv.data.model.DEFAULT_USER_AGENT
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.model.VideoDecoder
import com.example.iptv.data.network.PanelUrl
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the Xtream session and the user preferences.
 *
 * The password is encrypted with an AES/GCM key held by the Android Keystore, so a backup of
 * the SharedPreferences file alone cannot reveal the credentials. If a device has a broken
 * Keystore (some cheap TV boxes), the app degrades gracefully instead of crashing: the password
 * is then stored obfuscated only, and the session simply has to be renewed if that fails too.
 */
data class SavedProfile(val id: String, val server: String, val username: String, val active: Boolean)

class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val profiles = context.applicationContext
        .getSharedPreferences(PROFILES_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------- session

    fun credentials(): PanelCredentials? {
        val server = prefs.getString(KEY_SERVER, "").orEmpty()
        val user = prefs.getString(KEY_USERNAME, "").orEmpty()
        val password = readPassword().orEmpty()
        if (server.isBlank() || user.isBlank() || password.isBlank()) return null
        return PanelCredentials(serverUrl = server, username = user, password = password)
    }

    fun saveCredentials(credentials: PanelCredentials) {
        val previous = credentials()
        if (previous != null) snapshotActiveProfile()
        val id = profileId(credentials)
        if (previous == null || profileId(previous) != id) {
            // Restore this profile's settings, never another account's XMLTV URL or preferences.
            val stored = profiles.getString("profile.$id", null)
            if (stored == null || !restoreSnapshot(stored)) prefs.edit().clear().apply()
        }
        prefs.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, encrypt(credentials.password))
            .apply()
        snapshotActiveProfile()
    }

    fun hasSession(): Boolean = credentials()?.isComplete == true

    fun saveAccount(account: AccountInfo) {
        prefs.edit()
            .putString(KEY_STATUS, account.status)
            .putBoolean(KEY_ACTIVE, account.isActive)
            .putBoolean(KEY_TRIAL, account.isTrial)
            .putLong(KEY_EXPIRES_AT, account.expiresAtMillis ?: 0L)
            .putLong(KEY_CREATED_AT, account.createdAtMillis ?: 0L)
            .putInt(KEY_MAX_CONNECTIONS, account.maxConnections)
            .putInt(KEY_ACTIVE_CONNECTIONS, account.activeConnections)
            .putString(KEY_ALLOWED_FORMATS, account.allowedFormats.joinToString(","))
            .putString(KEY_PROTOCOL, account.serverProtocol)
            .putString(KEY_PORT, account.serverPort)
            .putString(KEY_TIMEZONE, account.timezone)
            .putString(KEY_PANEL_URL, account.panelUrl)
            .putLong(KEY_SYNCED_AT, account.syncedAtMillis)
            .apply()
        snapshotActiveProfile()
    }

    fun cachedAccount(): AccountInfo? {
        val server = prefs.getString(KEY_SERVER, "").orEmpty()
        if (server.isBlank()) return null
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        return AccountInfo(
            username = username,
            status = prefs.getString(KEY_STATUS, "Active").orEmpty(),
            isActive = prefs.getBoolean(KEY_ACTIVE, true),
            isTrial = prefs.getBoolean(KEY_TRIAL, false),
            // Older builds cached Xtream seconds as if they were milliseconds.
            expiresAtMillis = cachedEpochMillis(KEY_EXPIRES_AT),
            createdAtMillis = cachedEpochMillis(KEY_CREATED_AT),
            maxConnections = prefs.getInt(KEY_MAX_CONNECTIONS, 1),
            activeConnections = prefs.getInt(KEY_ACTIVE_CONNECTIONS, 0),
            allowedFormats = prefs.getString(KEY_ALLOWED_FORMATS, "")
                .orEmpty().split(",").filter { it.isNotBlank() },
            serverProtocol = prefs.getString(KEY_PROTOCOL, "http").orEmpty(),
            serverPort = prefs.getString(KEY_PORT, "").orEmpty(),
            timezone = prefs.getString(KEY_TIMEZONE, "UTC").orEmpty(),
            panelUrl = prefs.getString(KEY_PANEL_URL, server).orEmpty(),
            syncedAtMillis = prefs.getLong(KEY_SYNCED_AT, 0L)
        )
    }

    private fun cachedEpochMillis(key: String): Long? = prefs.getLong(key, 0L).takeIf { it > 0L }?.let {
        if (it < 10_000_000_000L) it * 1000L else it
    }

    // ------------------------------------------------------------ preferences

    fun loadSettings(): UserSettings {
        val format = when (prefs.getString(KEY_STREAM_FORMAT, StreamFormat.TS.name)) {
            StreamFormat.HLS.name -> StreamFormat.HLS
            else -> StreamFormat.TS
        }
        val decoder = when (prefs.getString(KEY_DECODER, VideoDecoder.AUTO.name)) {
            VideoDecoder.HARDWARE.name -> VideoDecoder.HARDWARE
            VideoDecoder.COMPATIBILITY.name -> VideoDecoder.COMPATIBILITY
            else -> VideoDecoder.AUTO
        }
        return UserSettings(
            streamFormat = format,
            bufferSeconds = prefs.getInt(KEY_BUFFER_SECONDS, 15).coerceIn(5, 60),
            decoder = decoder,
            lastEpgSyncMillis = prefs.getLong(KEY_EPG_SYNC, 0L),
            autoUpdateEpg = prefs.getBoolean(KEY_EPG_AUTO, true),
            customXmltvUrl = readSecret(prefs.getString(KEY_EPG_URL, "")).orEmpty(),
            epgIntervalHours = prefs.getInt(KEY_EPG_INTERVAL, 12).coerceIn(6, 48),
            epgOffsetHours = prefs.getInt(KEY_EPG_OFFSET, 0).coerceIn(-12, 12),
            time24Hour = prefs.getBoolean(KEY_TIME_24, true),
            showChannelEpg = prefs.getBoolean(KEY_SHOW_EPG, true),
            onlyChannelsWithEpg = prefs.getBoolean(KEY_ONLY_EPG, false),
            showFullEpg = prefs.getBoolean(KEY_FULL_EPG, true),
            subtitlesEnabled = prefs.getBoolean(KEY_SUBTITLES, true),
            subtitleSizePercent = prefs.getInt(KEY_SUBTITLE_SIZE, 100).coerceIn(75, 150),
            autoplayNextEpisode = prefs.getBoolean(KEY_NEXT_EPISODE, false),
            userAgent = prefs.getString(KEY_USER_AGENT, DEFAULT_USER_AGENT).orEmpty().ifBlank { DEFAULT_USER_AGENT },
            tmdbApiKey = readSecret(prefs.getString(KEY_TMDB_API_KEY, "")).orEmpty(),
            multiScreens = prefs.getInt(KEY_MULTI_SCREENS, 4).coerceIn(1, 4),
            livePlayer = playbackApp(KEY_LIVE_PLAYER), moviePlayer = playbackApp(KEY_MOVIE_PLAYER),
            seriesPlayer = playbackApp(KEY_SERIES_PLAYER),
            parentalSalt = prefs.getString(KEY_PARENTAL_SALT, "").orEmpty(),
            parentalHash = prefs.getString(KEY_PARENTAL_HASH, "").orEmpty(),
            lockedCategories = prefs.getStringSet(KEY_LOCKED_CATEGORIES, emptySet()).orEmpty().toSet()
        )
    }

    private fun playbackApp(key: String): PlaybackApp =
        if (prefs.getString(key, PlaybackApp.INTERNAL.name) == PlaybackApp.EXTERNAL.name) PlaybackApp.EXTERNAL else PlaybackApp.INTERNAL

    fun saveSettings(settings: UserSettings) {
        prefs.edit()
            .putString(KEY_STREAM_FORMAT, settings.streamFormat.name)
            .putString(KEY_DECODER, settings.decoder.name)
            .putInt(KEY_BUFFER_SECONDS, settings.bufferSeconds)
            .putLong(KEY_EPG_SYNC, settings.lastEpgSyncMillis)
            .putBoolean(KEY_EPG_AUTO, settings.autoUpdateEpg)
            .putString(KEY_EPG_URL, settings.customXmltvUrl.takeIf { it.isNotBlank() }?.let(::encrypt).orEmpty())
            .putInt(KEY_EPG_INTERVAL, settings.epgIntervalHours.coerceIn(6, 48))
            .putInt(KEY_EPG_OFFSET, settings.epgOffsetHours.coerceIn(-12, 12))
            .putBoolean(KEY_TIME_24, settings.time24Hour)
            .putBoolean(KEY_SHOW_EPG, settings.showChannelEpg)
            .putBoolean(KEY_ONLY_EPG, settings.onlyChannelsWithEpg)
            .putBoolean(KEY_FULL_EPG, settings.showFullEpg)
            .putBoolean(KEY_SUBTITLES, settings.subtitlesEnabled)
            .putInt(KEY_SUBTITLE_SIZE, settings.subtitleSizePercent.coerceIn(75, 150))
            .putBoolean(KEY_NEXT_EPISODE, settings.autoplayNextEpisode)
            .putString(KEY_USER_AGENT, settings.userAgent)
            .putString(KEY_TMDB_API_KEY, settings.tmdbApiKey.takeIf { it.isNotBlank() }?.let(::encrypt).orEmpty())
            .putInt(KEY_MULTI_SCREENS, settings.multiScreens.coerceIn(1, 4))
            .putString(KEY_LIVE_PLAYER, settings.livePlayer.name)
            .putString(KEY_MOVIE_PLAYER, settings.moviePlayer.name)
            .putString(KEY_SERIES_PLAYER, settings.seriesPlayer.name)
            .putString(KEY_PARENTAL_SALT, settings.parentalSalt)
            .putString(KEY_PARENTAL_HASH, settings.parentalHash)
            .putStringSet(KEY_LOCKED_CATEGORIES, settings.lockedCategories.toSet())
            .apply()
        snapshotActiveProfile()
    }

    /** Migrate old single-account installs without changing their existing data. */
    fun savedProfiles(): List<SavedProfile> {
        val current = credentials()
        if (current != null && profiles.getString("profile.${profileId(current)}", null) == null) snapshotActiveProfile()
        val activeId = current?.let(::profileId)
        return profiles.getStringSet(PROFILE_IDS, emptySet()).orEmpty().mapNotNull { id ->
            val json = profiles.getString("profile.$id", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return@mapNotNull null
            val server = json.optString(KEY_SERVER)
            val username = json.optString(KEY_USERNAME)
            if (server.isBlank() || username.isBlank()) null else SavedProfile(id, server, username, id == activeId)
        }.sortedWith(compareByDescending<SavedProfile> { it.active }.thenBy { it.username.lowercase() }.thenBy { it.server })
    }

    fun credentialsForProfile(id: String): PanelCredentials? {
        val json = profiles.getString("profile.$id", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val password = readSecret(json.optString(KEY_PASSWORD)).orEmpty()
        return PanelCredentials(json.optString(KEY_SERVER), json.optString(KEY_USERNAME), password).takeIf { it.isComplete }
    }

    /** The selected profile is restored locally, so switching also works offline. */
    fun activateProfile(id: String): AccountInfo? {
        val stored = profiles.getString("profile.$id", null) ?: return null
        val json = runCatching { JSONObject(stored) }.getOrNull() ?: return null
        if (readSecret(json.optString(KEY_PASSWORD)).isNullOrBlank() || json.optString(KEY_SERVER).isBlank() ||
            json.optString(KEY_USERNAME).isBlank()) return null
        snapshotActiveProfile()
        if (!restoreSnapshot(stored)) return null
        return cachedAccount()
    }

    fun removeProfile(id: String): Boolean {
        if (credentials()?.let(::profileId) == id) return false
        if (id !in profiles.getStringSet(PROFILE_IDS, emptySet()).orEmpty()) return false
        profiles.edit().remove("profile.$id")
            .putStringSet(PROFILE_IDS, profiles.getStringSet(PROFILE_IDS, emptySet()).orEmpty() - id).apply()
        return true
    }

    /** Leave a profile without deleting it, for example when opening demo mode. */
    fun deactivate() {
        snapshotActiveProfile()
        prefs.edit().clear().apply()
    }

    /** Logging out removes only the active profile; other profiles stay available. */
    fun clear() {
        val id = credentials()?.let(::profileId)
        prefs.edit().clear().apply()
        if (id != null) removeProfile(id)
    }

    private fun snapshotActiveProfile() {
        val current = credentials() ?: return
        val json = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String, is Int, is Long, is Boolean, is Float -> json.put(key, value)
                is Set<*> -> json.put(key, JSONArray(value.filterIsInstance<String>()))
            }
        }
        val id = profileId(current)
        profiles.edit().putString("profile.$id", json.toString())
            .putStringSet(PROFILE_IDS, profiles.getStringSet(PROFILE_IDS, emptySet()).orEmpty() + id).apply()
    }

    private fun restoreSnapshot(stored: String): Boolean = runCatching {
        val json = JSONObject(stored)
        val editor = prefs.edit().clear()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Number -> when {
                    key in LONG_KEYS -> editor.putLong(key, value.toLong())
                    value is Double || value is Float -> editor.putFloat(key, value.toFloat())
                    else -> editor.putInt(key, value.toInt())
                }
                is JSONArray -> editor.putStringSet(key, (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }.toSet())
            }
        }
        editor.apply()
        true
    }.getOrDefault(false)

    private fun profileId(credentials: PanelCredentials): String {
        val input = "${PanelUrl.normalize(credentials.serverUrl)}\u0000${credentials.username}"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    // --------------------------------------------------------------- password

    private fun readPassword(): String? = readSecret(prefs.getString(KEY_PASSWORD, null))

    // Custom XMLTV URLs may also contain credentials or tokens.
    private fun readSecret(value: String?): String? {
        val stored = value?.takeIf { it.isNotBlank() } ?: return null
        return try {
            when {
                stored.startsWith(ENC_PREFIX) -> decrypt(stored.removePrefix(ENC_PREFIX))
                stored.startsWith(OBFUSCATED_PREFIX) ->
                    String(Base64.decode(stored.removePrefix(OBFUSCATED_PREFIX), Base64.DEFAULT), Charsets.UTF_8)
                else -> stored
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun encrypt(plain: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            ENC_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (_: Throwable) {
            // Keystore unavailable: store a reversible obfuscation instead of failing the login.
            OBFUSCATED_PREFIX + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decrypt(payload: String): String {
        val key = existingKey() ?: error("Chave do Android Keystore indisponível")
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        require(bytes.size > IV_SIZE) { "Payload criptografado inválido" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
        return String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "iptv_native_session"
        private const val PROFILES_NAME = "iptv_native_profiles"
        private const val PROFILE_IDS = "profile_ids"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "iptv_native_session_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val ENC_PREFIX = "enc:v1:"
        private const val OBFUSCATED_PREFIX = "b64:v1:"

        private const val KEY_SERVER = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_STATUS = "account_status"
        private const val KEY_ACTIVE = "account_active"
        private const val KEY_TRIAL = "account_trial"
        private const val KEY_EXPIRES_AT = "account_expires_at"
        private const val KEY_CREATED_AT = "account_created_at"
        private const val KEY_MAX_CONNECTIONS = "account_max_connections"
        private const val KEY_ACTIVE_CONNECTIONS = "account_active_connections"
        private const val KEY_ALLOWED_FORMATS = "account_allowed_formats"
        private const val KEY_PROTOCOL = "account_protocol"
        private const val KEY_PORT = "account_port"
        private const val KEY_TIMEZONE = "account_timezone"
        private const val KEY_PANEL_URL = "account_panel_url"
        private const val KEY_SYNCED_AT = "account_synced_at"
        private const val KEY_STREAM_FORMAT = "setting_stream_format"
        private const val KEY_DECODER = "setting_decoder"
        private const val KEY_BUFFER_SECONDS = "setting_buffer_seconds"
        private const val KEY_EPG_SYNC = "setting_epg_sync"
        private const val KEY_EPG_AUTO = "setting_epg_auto"
        private const val KEY_EPG_URL = "setting_epg_url"
        private const val KEY_EPG_INTERVAL = "setting_epg_interval"
        private const val KEY_EPG_OFFSET = "setting_epg_offset"
        private const val KEY_TIME_24 = "setting_time_24"
        private const val KEY_SHOW_EPG = "setting_show_epg"
        private const val KEY_ONLY_EPG = "setting_only_epg"
        private const val KEY_FULL_EPG = "setting_full_epg"
        private const val KEY_SUBTITLES = "setting_subtitles"
        private const val KEY_SUBTITLE_SIZE = "setting_subtitle_size"
        private const val KEY_NEXT_EPISODE = "setting_next_episode"
        private const val KEY_USER_AGENT = "setting_user_agent"
        private const val KEY_TMDB_API_KEY = "setting_tmdb_api_key"
        private const val KEY_MULTI_SCREENS = "setting_multi_screens"
        private const val KEY_LIVE_PLAYER = "setting_live_player"
        private const val KEY_MOVIE_PLAYER = "setting_movie_player"
        private const val KEY_SERIES_PLAYER = "setting_series_player"
        private const val KEY_PARENTAL_SALT = "setting_parental_salt"
        private const val KEY_PARENTAL_HASH = "setting_parental_hash"
        private const val KEY_LOCKED_CATEGORIES = "setting_locked_categories"
        private val LONG_KEYS = setOf(KEY_EXPIRES_AT, KEY_CREATED_AT, KEY_SYNCED_AT, KEY_EPG_SYNC)
    }
}
