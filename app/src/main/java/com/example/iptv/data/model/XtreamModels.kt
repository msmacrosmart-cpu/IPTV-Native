package com.example.iptv.data.model

/** Session credentials for a Xtream Codes panel. */
data class PanelCredentials(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
) {
    val isComplete: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/** Account information returned by `player_api.php` (no action). */
data class AccountInfo(
    val username: String = "",
    val status: String = "Unknown",
    val message: String = "",
    val isActive: Boolean = false,
    val isTrial: Boolean = false,
    val expiresAtMillis: Long? = null,
    val createdAtMillis: Long? = null,
    val maxConnections: Int = 1,
    val activeConnections: Int = 0,
    val allowedFormats: List<String> = emptyList(),
    val serverProtocol: String = "http",
    val serverPort: String = "",
    val timezone: String = "UTC",
    val panelUrl: String = "",
    val syncedAtMillis: Long = System.currentTimeMillis()
) {
    val supportsHls: Boolean
        get() = allowedFormats.isEmpty() || allowedFormats.any { it.contains("m3u8", true) || it.contains("hls", true) }

    val statusLabel: String
        get() = when {
            !isActive -> "Inativa"
            expiresAtMillis != null && expiresAtMillis in 1..System.currentTimeMillis() -> "Expirada"
            isTrial -> "Período de teste"
            else -> "Ativa"
        }
}

enum class StreamFormat(val label: String, val extension: String) {
    TS("MPEG-TS", "ts"),
    HLS("HLS (m3u8)", "m3u8");

    fun other(): StreamFormat = if (this == TS) HLS else TS
}

enum class VideoDecoder(val label: String) {
    AUTO("Automático"),
    HARDWARE("Hardware (preferencial)"),
    COMPATIBILITY("Compatibilidade (fallback SW)")
}

data class UserSettings(
    val streamFormat: StreamFormat = StreamFormat.TS,
    val bufferSeconds: Int = 15,
    val decoder: VideoDecoder = VideoDecoder.AUTO,
    val lastEpgSyncMillis: Long = 0L
)

data class LiveCategory(
    val id: String,
    val name: String
)

data class LiveChannel(
    val id: Int,
    val name: String,
    val streamNumber: Int = 0,
    val icon: String? = null,
    val epgChannelId: String? = null,
    val categoryId: String? = null,
    val hasArchive: Boolean = false
)

data class EpgEntry(
    val id: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val isNowPlaying: Boolean
) {
    val durationMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(0L)

    fun progress(nowMillis: Long): Float {
        if (durationMillis <= 0L) return 0f
        val elapsed = nowMillis - startMillis
        return (elapsed.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }

    fun isCurrent(nowMillis: Long): Boolean = nowMillis in startMillis until endMillis
}

data class VodCategory(
    val id: String,
    val name: String
)

data class VodItem(
    val id: Int,
    val name: String,
    val icon: String? = null,
    val rating: String? = null,
    val rating5: Double? = null,
    val extension: String = "mp4",
    val categoryId: String? = null,
    val plot: String? = null,
    val addedAtMillis: Long? = null
)

data class SeriesCategory(
    val id: String,
    val name: String
)

data class SeriesItem(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val rating5: Double? = null,
    val backdrop: String? = null,
    val categoryId: String? = null,
    val episodeCount: Int? = null
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val extension: String = "mp4",
    val plot: String? = null,
    val durationLabel: String? = null,
    val image: String? = null
)

data class SeasonEpisodes(
    val season: Int,
    val episodes: List<Episode>
)

data class VodDetails(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val durationLabel: String? = null,
    val backdrop: String? = null,
    val rating: String? = null
)

/** Everything the player needs to start a stream. */
data class PlaybackRequest(
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val fallbackUrl: String? = null,
    val isLive: Boolean = false,
    val channelId: Int? = null,
    val seriesId: Int? = null
)
