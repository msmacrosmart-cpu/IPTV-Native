package com.example.iptv.data.repository

import android.net.Uri
import android.util.Log
import com.example.iptv.data.model.AccountInfo
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.Episode
import com.example.iptv.data.model.EpgEntry
import com.example.iptv.data.model.LiveCategory
import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.SeasonEpisodes
import com.example.iptv.data.model.SeriesCategory
import com.example.iptv.data.model.SeriesItem
import com.example.iptv.data.model.StreamFormat
import com.example.iptv.data.model.UserSettings
import com.example.iptv.data.model.VideoDecoder
import com.example.iptv.data.model.VodCategory
import com.example.iptv.data.model.VodDetails
import com.example.iptv.data.model.VodItem
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.network.PanelUrl
import com.example.iptv.data.network.XtreamActions
import com.example.iptv.data.network.XtreamApiService
import com.example.iptv.data.network.XtreamParser
import com.example.iptv.data.preferences.SessionManager
import com.example.iptv.data.preferences.SavedProfile
import com.example.iptv.util.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for the Xtream Codes session and catalogue.
 *
 * Every call returns an [ApiResult] with a human-readable Portuguese error, so a bad panel URL,
 * expired account or missing permission never results in an empty/black screen without feedback.
 */
class XtreamRepository(private val session: SessionManager) {

    private val _account = MutableStateFlow(session.cachedAccount())
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _settings = MutableStateFlow(session.loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    @Volatile
    private var credentials: PanelCredentials? = session.credentials()

    @Volatile
    private var api: XtreamApiService? = null

    private val serviceLock = Any()
    private val sessionGeneration = AtomicLong(0)

    // ------------------------------------------------------------------ session

    fun hasStoredSession(): Boolean = session.hasSession()

    fun cachedAccount(): AccountInfo? = _account.value

    fun currentCredentials(): PanelCredentials? = credentials

    fun savedProfiles(): List<SavedProfile> = synchronized(serviceLock) { session.savedProfiles() }

    /** Switch synchronously to the encrypted local profile, then validate it in the background. */
    fun activateProfile(id: String): AccountInfo? = synchronized(serviceLock) {
        val account = session.activateProfile(id) ?: return@synchronized null
        val selected = session.credentials() ?: return@synchronized null
        sessionGeneration.incrementAndGet()
        credentials = selected
        api = null
        _account.value = account
        _settings.value = session.loadSettings()
        account
    }

    fun removeProfile(id: String): PanelCredentials? = synchronized(serviceLock) {
        val saved = session.credentialsForProfile(id) ?: return@synchronized null
        if (session.removeProfile(id)) saved else null
    }

    /** Distinguishes logout/re-login even when the same credentials are used again. */
    fun sessionToken(expected: PanelCredentials): Long? = synchronized(serviceLock) {
        sessionGeneration.get().takeIf { credentials == expected }
    }

    fun isSessionCurrent(expected: PanelCredentials, token: Long): Boolean = synchronized(serviceLock) {
        credentials == expected && sessionGeneration.get() == token
    }

    /**
     * Validates the stored credentials against the panel. Used on cold start so a session
     * survives app restarts (and reports expired accounts instead of silently failing).
     */
    suspend fun restoreSession(): ApiResult<AccountInfo> {
        val stored = credentials ?: return ApiResult.Failure("Nenhuma sessão salva.")
        return login(stored.serverUrl, stored.username, stored.password, persist = false)
    }

    suspend fun login(
        rawServer: String,
        rawUsername: String,
        rawPassword: String,
        persist: Boolean = true
    ): ApiResult<AccountInfo> = withContext(Dispatchers.IO) {
        val generation = sessionGeneration.get()
        val parsed = PanelUrl.parse(rawServer)
        val server = parsed.baseUrl
        val username = rawUsername.trim().ifBlank { parsed.username.orEmpty() }
        val password = rawPassword.trim().ifBlank { parsed.password.orEmpty() }

        if (server.isBlank()) {
            return@withContext ApiResult.Failure(
                "Informe a URL do painel Xtream, por exemplo: http://servidor.com:8080"
            )
        }
        if (username.isBlank()) {
            return@withContext ApiResult.Failure("Informe o usuário da sua conta Xtream.")
        }
        if (password.isBlank()) {
            return@withContext ApiResult.Failure("Informe a senha da sua conta Xtream.")
        }

        try {
            val service = ApiClient.create(server)
            val response = service.authenticate(username, password)
            val body = response.body()?.use { it.string() }.orEmpty()

            if (body.isBlank()) {
                return@withContext ApiResult.Failure(httpErrorMessage(response.code()))
            }

            val root = runCatching { ApiClient.json.parseToJsonElement(body) }.getOrNull()
                ?: return@withContext ApiResult.Failure(
                    "O servidor respondeu em um formato inesperado. Confirme se a URL é de um " +
                        "painel Xtream Codes (player_api.php)."
                )

            val account = XtreamParser.parseAccount(root, server)
            if (!account.isActive || account.expiresAtMillis?.let { it <= System.currentTimeMillis() } == true) {
                return@withContext ApiResult.Failure(accountErrorMessage(account, response.code()))
            }

            val sanitized = account.copy(
                username = account.username.ifBlank { username },
                panelUrl = server,
                syncedAtMillis = System.currentTimeMillis()
            )

            val newCredentials = PanelCredentials(server, username, password)
            currentCoroutineContext().ensureActive()
            synchronized(serviceLock) {
                // A late account refresh must not restore a session after the user has logged out.
                if (generation != sessionGeneration.get()) return@withContext ApiResult.Failure("Sessão encerrada.")
                if (credentials != newCredentials) sessionGeneration.incrementAndGet()
                credentials = newCredentials
                api = service
                if (persist) {
                    session.saveCredentials(newCredentials)
                    _settings.value = session.loadSettings()
                }
                session.saveAccount(sanitized)
                _account.value = sanitized
            }
            ApiResult.Success(sanitized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            ApiResult.Failure(
                "Tempo esgotado ao conectar em $server. Verifique a porta, a internet e se o painel está online."
            )
        } catch (e: UnknownHostException) {
            ApiResult.Failure("Servidor não encontrado: ${e.message ?: server}. Confira a URL digitada.")
        } catch (e: ConnectException) {
            ApiResult.Failure("Conexão recusada por $server. A porta está correta?")
        } catch (e: javax.net.ssl.SSLException) {
            ApiResult.Failure("Falha de segurança (SSL) ao conectar. Tente a URL com http:// ou https://.")
        } catch (e: IOException) {
            ApiResult.Failure("Falha de rede. Verifique a conexão e o endereço do painel.")
        } catch (e: Exception) {
            Log.w(TAG, "Falha no login", e)
            ApiResult.Failure("Não foi possível autenticar: ${e.message ?: "erro desconhecido"}")
        }
    }

    fun logout() = clearSession(removeProfile = true)

    fun deactivate() = clearSession(removeProfile = false)

    private fun clearSession(removeProfile: Boolean) = synchronized(serviceLock) {
        sessionGeneration.incrementAndGet()
        if (removeProfile) session.clear() else session.deactivate()
        credentials = null
        api = null
        _account.value = null
        _settings.value = UserSettings()
    }

    // ------------------------------------------------------------------- catalog

    suspend fun liveCategories(): ApiResult<List<LiveCategory>> =
        withApi({ XtreamParser.parseLiveCategories(it) }, expectArray = true) { service, creds ->
            service.categories(creds.username, creds.password, XtreamActions.LIVE_CATEGORIES)
        }

    suspend fun liveChannels(categoryId: String? = null): ApiResult<List<LiveChannel>> =
        withApi({ XtreamParser.parseLiveChannels(it) }, expectArray = true) { service, creds ->
            service.streams(creds.username, creds.password, XtreamActions.LIVE_STREAMS, categoryId)
        }

    suspend fun shortEpg(streamId: Int, limit: Int = 10): ApiResult<List<EpgEntry>> =
        withApi({ XtreamParser.parseShortEpg(it) }) { service, creds ->
            service.shortEpg(creds.username, creds.password, XtreamActions.SHORT_EPG, streamId, limit)
        }

    suspend fun vodCategories(): ApiResult<List<VodCategory>> =
        withApi({ XtreamParser.parseVodCategories(it) }, expectArray = true) { service, creds ->
            service.categories(creds.username, creds.password, XtreamActions.VOD_CATEGORIES)
        }

    suspend fun vodItems(categoryId: String? = null): ApiResult<List<VodItem>> =
        withApi({ XtreamParser.parseVodStreams(it) }, expectArray = true) { service, creds ->
            service.streams(creds.username, creds.password, XtreamActions.VOD_STREAMS, categoryId)
        }

    suspend fun vodDetails(vodId: Int): ApiResult<VodDetails> =
        withApi({ XtreamParser.parseVodInfo(it) }) { service, creds ->
            service.vodInfo(creds.username, creds.password, XtreamActions.VOD_INFO, vodId)
        }

    suspend fun seriesCategories(): ApiResult<List<SeriesCategory>> =
        withApi({ XtreamParser.parseSeriesCategories(it) }, expectArray = true) { service, creds ->
            service.categories(creds.username, creds.password, XtreamActions.SERIES_CATEGORIES)
        }

    suspend fun series(categoryId: String? = null): ApiResult<List<SeriesItem>> =
        withApi({ XtreamParser.parseSeries(it) }, expectArray = true) { service, creds ->
            service.streams(creds.username, creds.password, XtreamActions.SERIES, categoryId)
        }

    suspend fun seriesInfo(seriesId: Int): ApiResult<List<SeasonEpisodes>> =
        withApi({ XtreamParser.parseSeriesInfo(it) }) { service, creds ->
            service.seriesInfo(creds.username, creds.password, XtreamActions.SERIES_INFO, seriesId)
        }

    /** Parse details and episodes from the same panel response, without a duplicate request. */
    suspend fun seriesInfoWithDetails(seriesId: Int): ApiResult<Pair<VodDetails, List<SeasonEpisodes>>> =
        withApi({ XtreamParser.parseVodInfo(it) to XtreamParser.parseSeriesInfo(it) }) { service, creds ->
            service.seriesInfo(creds.username, creds.password, XtreamActions.SERIES_INFO, seriesId)
        }

    /** Round-trip latency to the panel, used by the settings screen. */
    suspend fun ping(): ApiResult<Long> = withContext(Dispatchers.IO) {
        val creds = credentials ?: return@withContext ApiResult.Failure("Sessão expirada.")
        val started = android.os.SystemClock.elapsedRealtime()
        when (
            val result = withApi({ it }) { service, _ ->
                service.categories(creds.username, creds.password, XtreamActions.LIVE_CATEGORIES)
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(android.os.SystemClock.elapsedRealtime() - started)
            is ApiResult.Failure -> result
        }
    }

    // ------------------------------------------------------------------ settings

    fun updateStreamFormat(format: StreamFormat) {
        updateSettings(_settings.value.copy(streamFormat = format))
    }

    fun updateBufferSeconds(seconds: Int) {
        updateSettings(_settings.value.copy(bufferSeconds = seconds.coerceIn(5, 60)))
    }

    fun updateDecoder(decoder: VideoDecoder) {
        updateSettings(_settings.value.copy(decoder = decoder))
    }

    fun updateEpgAuto(enabled: Boolean) {
        updateSettings(_settings.value.copy(autoUpdateEpg = enabled))
    }

    fun updateCustomXmltvUrl(url: String) {
        updateSettings(_settings.value.copy(customXmltvUrl = url, lastEpgSyncMillis = 0L))
    }

    fun clearEpgSync() {
        updateSettings(_settings.value.copy(lastEpgSyncMillis = 0L))
    }

    fun markEpgSyncedIfActive(expected: PanelCredentials, token: Long? = null): Boolean = synchronized(serviceLock) {
        if (credentials != expected || (token != null && sessionGeneration.get() != token)) return@synchronized false
        updateSettings(_settings.value.copy(lastEpgSyncMillis = System.currentTimeMillis()))
        true
    }

    fun updateSettings(newSettings: UserSettings) = synchronized(serviceLock) {
        val safe = newSettings.copy(
            bufferSeconds = newSettings.bufferSeconds.coerceIn(5, 60),
            epgIntervalHours = newSettings.epgIntervalHours.coerceIn(6, 48),
            catalogIntervalDays = newSettings.catalogIntervalDays.coerceIn(1, 3),
            epgOffsetHours = newSettings.epgOffsetHours.coerceIn(-12, 12),
            subtitleSizePercent = newSettings.subtitleSizePercent.coerceIn(75, 150),
        )
        _settings.update { safe }
        session.saveSettings(safe)
    }

    // ---------------------------------------------------------------------- URLs

    fun liveStreamUrl(channelId: Int, format: StreamFormat, forCredentials: PanelCredentials? = credentials): String {
        val creds = forCredentials ?: return ""
        val base = PanelUrl.normalize(creds.serverUrl)
        return buildString {
            append(base)
            append("live/")
            append(encode(creds.username))
            append('/')
            append(encode(creds.password))
            append('/')
            append(channelId)
            append('.')
            append(format.extension)
        }
    }

    fun movieStreamUrl(item: VodItem, forCredentials: PanelCredentials? = credentials): String {
        val creds = forCredentials ?: return ""
        val base = PanelUrl.normalize(creds.serverUrl)
        val extension = item.extension.trim().ifBlank { "mp4" }
        return buildString {
            append(base)
            append("movie/")
            append(encode(creds.username))
            append('/')
            append(encode(creds.password))
            append('/')
            append(item.id)
            append('.')
            append(extension)
        }
    }

    fun episodeStreamUrl(episode: Episode): String {
        val creds = credentials ?: return ""
        val base = PanelUrl.normalize(creds.serverUrl)
        val extension = episode.extension.trim().ifBlank { "mp4" }
        return buildString {
            append(base)
            append("series/")
            append(encode(creds.username))
            append('/')
            append(encode(creds.password))
            append('/')
            append(episode.id)
            append('.')
            append(extension)
        }
    }

    fun epgSyncLabel(): String {
        val millis = _settings.value.lastEpgSyncMillis
        return if (millis <= 0L) "Nunca atualizado" else FormatUtils.formatDayAndTime(millis)
    }

    private fun encode(value: String): String = try {
        Uri.encode(value)
    } catch (_: Exception) {
        value
    }

    // ---------------------------------------------------------------- internals

    /**
     * Executes a player_api call, validates the HTTP status, parses the body on the IO
     * dispatcher and converts every failure mode into a readable message.
     */
    private suspend fun <T> withApi(
        parse: (JsonElement) -> T,
        expectArray: Boolean = false,
        request: suspend (XtreamApiService, PanelCredentials) -> Response<ResponseBody>
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val creds = credentials
            ?: return@withContext ApiResult.Failure("Sessão não encontrada. Faça login novamente.")
        val token = sessionToken(creds) ?: return@withContext ApiResult.Failure("Sessão alterada.")

        val service = try {
            serviceFor(creds)
        } catch (e: Exception) {
            return@withContext ApiResult.Failure("URL do painel inválida: ${e.message ?: creds.serverUrl}")
        }

        try {
            val response = request(service, creds)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext ApiResult.Failure(httpErrorMessage(response.code()))
            }
            val body = response.body()?.use { it.string() }.orEmpty()

            if (body.isBlank()) {
                return@withContext ApiResult.Failure(httpErrorMessage(response.code()))
            }

            val root = runCatching { ApiClient.json.parseToJsonElement(body) }.getOrNull()
                ?: return@withContext ApiResult.Failure("Resposta inválida do painel (JSON malformado).")

            if (expectArray && root !is JsonArray) {
                return@withContext ApiResult.Failure("O painel não retornou uma lista válida; o cache existente foi preservado.")
            }
            val parsed = parse(root)
            if (!isSessionCurrent(creds, token)) return@withContext ApiResult.Failure("Sessão alterada.")
            ApiResult.Success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            ApiResult.Failure("O painel demorou demais para responder. Tente novamente.")
        } catch (e: UnknownHostException) {
            ApiResult.Failure("Servidor não encontrado. Confira a URL do painel.")
        } catch (e: ConnectException) {
            ApiResult.Failure("Conexão recusada pelo painel. Confira a porta.")
        } catch (e: IOException) {
            ApiResult.Failure("Falha de rede. Verifique a conexão e o endereço do painel.")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao consultar o painel", e)
            ApiResult.Failure("Erro ao consultar o painel: ${e.message ?: "desconhecido"}")
        }
    }

    private fun serviceFor(creds: PanelCredentials): XtreamApiService = synchronized(serviceLock) {
        // Never send one account's credentials to a service created for another account.
        check(credentials == creds) { "A sessão foi alterada." }
        api ?: ApiClient.create(creds.serverUrl).also { api = it }
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401 -> "Usuário ou senha recusados pelo painel (HTTP 401)."
        403 -> "Acesso bloqueado pelo painel (HTTP 403)."
        404 -> "player_api.php não encontrado (HTTP 404). Confira a URL e a porta do painel."
        in 500..599 -> "O painel está com problemas (HTTP $code). Tente novamente em instantes."
        else -> "O painel respondeu HTTP $code sem conteúdo."
    }

    private fun accountErrorMessage(account: AccountInfo, httpCode: Int): String = when {
        account.status.equals("Expired", ignoreCase = true) ->
            "Sua conta expirou em ${FormatUtils.formatDate(account.expiresAtMillis)}. Renove com o seu provedor."
        account.status.equals("Disabled", ignoreCase = true) ->
            "Sua conta está desativada. Entre em contato com o provedor."
        account.expiresAtMillis != null && account.expiresAtMillis in 1 until System.currentTimeMillis() ->
            "Sua conta expirou em ${FormatUtils.formatDate(account.expiresAtMillis)}."
        account.message.isNotBlank() -> account.message
        httpCode == 401 -> "Usuário ou senha inválidos (HTTP 401)."
        else -> "Não foi possível validar a conta (${account.status}). Confira usuário, senha e painel."
    }

    companion object {
        private const val TAG = "XtreamRepository"
    }
}
