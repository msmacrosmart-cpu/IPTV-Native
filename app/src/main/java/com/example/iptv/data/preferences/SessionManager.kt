package com.example.iptv.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.model.VideoDecoder
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
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -------------------------------------------------------------- session

    fun credentials(): PanelCredentials? {
        val server = prefs.getString(KEY_SERVER, "").orEmpty()
        val user = prefs.getString(KEY_USERNAME, "").orEmpty()
        val password = readPassword().orEmpty()
        if (server.isBlank() || user.isBlank() || password.isBlank()) return null
        return PanelCredentials(serverUrl = server, username = user, password = password)
    }

    fun saveCredentials(credentials: PanelCredentials) {
        prefs.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, encrypt(credentials.password))
            .apply()
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
            customXmltvUrl = readSecret(prefs.getString(KEY_EPG_URL, "")).orEmpty()
        )
    }

    fun saveSettings(settings: UserSettings) {
        prefs.edit()
            .putString(KEY_STREAM_FORMAT, settings.streamFormat.name)
            .putString(KEY_DECODER, settings.decoder.name)
            .putInt(KEY_BUFFER_SECONDS, settings.bufferSeconds)
            .putLong(KEY_EPG_SYNC, settings.lastEpgSyncMillis)
            .putBoolean(KEY_EPG_AUTO, settings.autoUpdateEpg)
            .putString(KEY_EPG_URL, settings.customXmltvUrl.takeIf { it.isNotBlank() }?.let(::encrypt).orEmpty())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
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
    }
}
