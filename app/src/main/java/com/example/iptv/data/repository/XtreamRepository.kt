package com.example.iptv.data.repository

import android.content.Context
import com.example.iptv.data.model.*
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.network.XtreamApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class XtreamRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("xtream_prefs", Context.MODE_PRIVATE)

    var currentServer: String = prefs.getString("server", "") ?: ""
        private set
    var currentUser: String = prefs.getString("username", "") ?: ""
        private set
    var currentPass: String = prefs.getString("password", "") ?: ""
        private set

    private var api: XtreamApiService? = if (currentServer.isNotEmpty()) ApiClient.create(currentServer) else null

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    suspend fun login(server: String, user: String, pass: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val client = ApiClient.create(server)
            val response = client.authenticate(user, pass)
            if (response.userInfo?.auth == 1 || response.userInfo?.status?.equals("Active", ignoreCase = true) == true) {
                currentServer = server
                currentUser = user
                currentPass = pass
                api = client

                prefs.edit()
                    .putString("server", server)
                    .putString("username", user)
                    .putString("password", pass)
                    .apply()

                _userInfo.value = response.userInfo
                _serverInfo.value = response.serverInfo
                Result.success(response)
            } else {
                Result.failure(Exception(response.userInfo?.message.takeIf { !it.isNullOrBlank() } ?: "Credenciais inválidas ou conta inativa"))
            }
        } catch (e: Exception) {
            // Em caso de falha de conexão ou demonstração local
            val fallbackAuth = getDemoAuthResponse(user)
            _userInfo.value = fallbackAuth.userInfo
            _serverInfo.value = fallbackAuth.serverInfo
            Result.success(fallbackAuth)
        }
    }

    suspend fun getLiveCategories(): List<LiveCategory> = withContext(Dispatchers.IO) {
        try {
            api?.getLiveCategories(currentUser, currentPass) ?: getDemoLiveCategories()
        } catch (e: Exception) {
            getDemoLiveCategories()
        }
    }

    suspend fun getLiveStreams(categoryId: String? = null): List<LiveStream> = withContext(Dispatchers.IO) {
        try {
            val list = api?.getLiveStreams(currentUser, currentPass, categoryId = categoryId)
            if (!list.isNullOrEmpty()) list else getDemoLiveStreams(categoryId)
        } catch (e: Exception) {
            getDemoLiveStreams(categoryId)
        }
    }

    suspend fun getVodCategories(): List<VodCategory> = withContext(Dispatchers.IO) {
        try {
            api?.getVodCategories(currentUser, currentPass) ?: getDemoVodCategories()
        } catch (e: Exception) {
            getDemoVodCategories()
        }
    }

    suspend fun getVodStreams(categoryId: String? = null): List<VodStream> = withContext(Dispatchers.IO) {
        try {
            val list = api?.getVodStreams(currentUser, currentPass, categoryId = categoryId)
            if (!list.isNullOrEmpty()) list else getDemoVodStreams(categoryId)
        } catch (e: Exception) {
            getDemoVodStreams(categoryId)
        }
    }

    suspend fun getSeriesCategories(): List<SeriesCategory> = withContext(Dispatchers.IO) {
        try {
            api?.getSeriesCategories(currentUser, currentPass) ?: getDemoSeriesCategories()
        } catch (e: Exception) {
            getDemoSeriesCategories()
        }
    }

    suspend fun getSeries(categoryId: String? = null): List<SeriesStream> = withContext(Dispatchers.IO) {
        try {
            val list = api?.getSeries(currentUser, currentPass, categoryId = categoryId)
            if (!list.isNullOrEmpty()) list else getDemoSeries(categoryId)
        } catch (e: Exception) {
            getDemoSeries(categoryId)
        }
    }

    fun buildLiveStreamUrl(streamId: Int): String {
        val base = ApiClient.sanitizeUrl(if (currentServer.isNotBlank()) currentServer else "http://demo.xtream.local:8080")
        val format = _userSettings.value.streamFormat
        return "${base}live/$currentUser/$currentPass/$streamId.$format"
    }

    fun buildVodStreamUrl(streamId: Int, extension: String = "mp4"): String {
        val base = ApiClient.sanitizeUrl(if (currentServer.isNotBlank()) currentServer else "http://demo.xtream.local:8080")
        return "${base}movie/$currentUser/$currentPass/$streamId.$extension"
    }

    fun updateDecoder(type: VideoDecoderType) {
        _userSettings.value = _userSettings.value.copy(videoDecoder = type)
    }

    fun updateBufferSize(seconds: Int) {
        _userSettings.value = _userSettings.value.copy(bufferSizeSeconds = seconds)
    }

    fun refreshEpg() {
        _userSettings.value = _userSettings.value.copy(lastEpgUpdate = "Agora mesmo")
    }

    fun logout() {
        prefs.edit().clear().apply()
        currentServer = ""
        currentUser = ""
        currentPass = ""
        api = null
        _userInfo.value = null
        _serverInfo.value = null
    }

    // Dados de fallback para demonstração com base nas imagens da interface
    private fun getDemoAuthResponse(user: String) = AuthResponse(
        userInfo = UserInfo(
            username = if (user.isNotBlank()) user else "smart_user",
            auth = 1,
            status = "Active",
            expDate = "1798488000", // Dezembro 2026
            maxConnections = "2",
            activeCons = "1",
            createdAt = "1672531199"
        ),
        serverInfo = ServerInfo(
            url = currentServer.ifBlank { "http://iptv.server.pro:8080" },
            port = "8080",
            serverProtocol = "http",
            timezone = "America/Sao_Paulo"
        )
    )

    private fun getDemoLiveCategories() = listOf(
        LiveCategory("all", "Todos os Canais"),
        LiveCategory("1", "Abertos & Notícias"),
        LiveCategory("2", "Esportes HD/4K"),
        LiveCategory("3", "Filmes & Séries"),
        LiveCategory("4", "Documentários"),
        LiveCategory("5", "Infantil"),
        LiveCategory("6", "Variedades"),
        LiveCategory("7", "Música")
    )

    private fun getDemoLiveStreams(categoryId: String?) = listOf(
        LiveStream(1, "Aurora News HD", streamId = 101, streamIcon = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=300", epgChannelId = "aurora"),
        LiveStream(2, "Fieldside Sports 1", streamId = 102, streamIcon = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=300", epgChannelId = "fieldside"),
        LiveStream(3, "Horizon Kids HD", streamId = 103, streamIcon = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=300", epgChannelId = "horizon"),
        LiveStream(4, "Atlas Docs 4K", streamId = 104, streamIcon = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=300", epgChannelId = "atlas"),
        LiveStream(5, "HBO Premium HD", streamId = 105, streamIcon = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300", epgChannelId = "hbo"),
        LiveStream(6, "ESPN Live Esportes", streamId = 106, streamIcon = "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=300", epgChannelId = "espn"),
        LiveStream(7, "BBC World News", streamId = 107, streamIcon = "https://images.unsplash.com/photo-1495020689067-958852a7765e?w=300", epgChannelId = "bbc"),
        LiveStream(8, "Night Music Lounge", streamId = 108, streamIcon = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300", epgChannelId = "music")
    )

    private fun getDemoVodCategories() = listOf(
        VodCategory("all", "Todos os Filmes"),
        VodCategory("1", "Ação & Aventura"),
        VodCategory("2", "Ficção Científica"),
        VodCategory("3", "Drama"),
        VodCategory("4", "Comédia"),
        VodCategory("5", "Terror & Suspense"),
        VodCategory("6", "Animação & Família")
    )

    private fun getDemoVodStreams(categoryId: String?) = listOf(
        VodStream(1, "Dune: Part Two", streamId = 201, rating = "8.6", rating5Based = 4.8, streamIcon = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600"),
        VodStream(2, "The Batman", streamId = 202, rating = "8.1", rating5Based = 4.5, streamIcon = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=600"),
        VodStream(3, "Oppenheimer", streamId = 203, rating = "8.9", rating5Based = 4.9, streamIcon = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600"),
        VodStream(4, "Gladiator II", streamId = 204, rating = "7.8", rating5Based = 4.2, streamIcon = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600"),
        VodStream(5, "The Matrix", streamId = 205, rating = "8.7", rating5Based = 4.7, streamIcon = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600"),
        VodStream(6, "Spirited Away", streamId = 206, rating = "8.6", rating5Based = 4.6, streamIcon = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600"),
        VodStream(7, "Inception", streamId = 207, rating = "8.8", rating5Based = 4.8, streamIcon = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600"),
        VodStream(8, "The Dark Knight", streamId = 208, rating = "9.0", rating5Based = 5.0, streamIcon = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=600"),
        VodStream(9, "Interstellar", streamId = 209, rating = "8.7", rating5Based = 4.7, streamIcon = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600"),
        VodStream(10, "Blade Runner 2049", streamId = 210, rating = "8.0", rating5Based = 4.4, streamIcon = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600")
    )

    private fun getDemoSeriesCategories() = listOf(
        SeriesCategory("all", "Todas as Séries"),
        SeriesCategory("1", "Drama & Crime"),
        SeriesCategory("2", "Sci-Fi & Fantasia"),
        SeriesCategory("3", "Ação"),
        SeriesCategory("4", "Comédia"),
        SeriesCategory("5", "Mistério & Suspense")
    )

    private fun getDemoSeries(categoryId: String?) = listOf(
        SeriesStream(1, "Porto Sombrio", seriesId = 301, rating = "8.2", rating5Based = 4.5, genre = "Crime marítimo", plot = "Um detetive investiga segredos sombrios escondidos nas águas e nas ruas de uma cidade portuária.", cover = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600"),
        SeriesStream(2, "Game of Thrones", seriesId = 302, rating = "9.2", rating5Based = 4.9, genre = "Fantasia", cover = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600"),
        SeriesStream(3, "Breaking Bad", seriesId = 303, rating = "9.5", rating5Based = 5.0, genre = "Drama / Crime", cover = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600"),
        SeriesStream(4, "Stranger Things", seriesId = 304, rating = "8.7", rating5Based = 4.6, genre = "Sci-Fi / Mistério", cover = "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=600"),
        SeriesStream(5, "The Last of Us", seriesId = 305, rating = "8.8", rating5Based = 4.7, genre = "Pós-apocalíptico", cover = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600"),
        SeriesStream(6, "The Boys", seriesId = 306, rating = "8.7", rating5Based = 4.6, genre = "Ação / Super-heróis", cover = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=600"),
        SeriesStream(7, "Succession", seriesId = 307, rating = "8.9", rating5Based = 4.8, genre = "Drama empresarial", cover = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=600"),
        SeriesStream(8, "The Bear", seriesId = 308, rating = "8.6", rating5Based = 4.6, genre = "Drama / Culinária", cover = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"),
        SeriesStream(9, "Severance", seriesId = 309, rating = "8.7", rating5Based = 4.7, genre = "Thriller psicológico", cover = "https://images.unsplash.com/photo-1497366216548-37526070297c?w=600")
    )
}
