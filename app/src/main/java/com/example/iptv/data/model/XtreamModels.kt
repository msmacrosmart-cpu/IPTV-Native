package com.example.iptv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    @SerialName("user_info")
    val userInfo: UserInfo? = null,
    @SerialName("server_info")
    val serverInfo: ServerInfo? = null
)

@Serializable
data class UserInfo(
    val username: String = "",
    val password: String = "",
    val message: String = "",
    val auth: Int = 0,
    val status: String = "Active",
    @SerialName("exp_date")
    val expDate: String? = null,
    @SerialName("is_trial")
    val isTrial: String = "0",
    @SerialName("active_cons")
    val activeCons: String = "0",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("max_connections")
    val maxConnections: String = "1",
    @SerialName("allowed_output_formats")
    val allowedOutputFormats: List<String> = emptyList()
)

@Serializable
data class ServerInfo(
    val url: String = "",
    val port: String = "",
    @SerialName("https_port")
    val httpsPort: String = "",
    @SerialName("server_protocol")
    val serverProtocol: String = "http",
    val rtmp_port: String = "",
    val timezone: String = "UTC",
    @SerialName("time_now")
    val timeNow: String = ""
)

@Serializable
data class LiveCategory(
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
    @SerialName("parent_id")
    val parentId: Int = 0
)

@Serializable
data class LiveStream(
    val num: Int? = null,
    val name: String,
    @SerialName("stream_type")
    val streamType: String = "live",
    @SerialName("stream_id")
    val streamId: Int,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("epg_channel_id")
    val epgChannelId: String? = null,
    val added: String? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("custom_sid")
    val customSid: String? = null,
    @SerialName("tv_archive")
    val tvArchive: Int = 0,
    @SerialName("direct_source")
    val directSource: String? = null
)

@Serializable
data class VodCategory(
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
    @SerialName("parent_id")
    val parentId: Int = 0
)

@Serializable
data class VodStream(
    val num: Int? = null,
    val name: String,
    @SerialName("stream_type")
    val streamType: String = "movie",
    @SerialName("stream_id")
    val streamId: Int,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    val rating: String? = null,
    @SerialName("rating_5based")
    val rating5Based: Double? = null,
    val added: String? = null,
    @SerialName("category_id")
    val categoryId: String? = null,
    @SerialName("container_extension")
    val containerExtension: String? = "mp4"
)

@Serializable
data class SeriesCategory(
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
    @SerialName("parent_id")
    val parentId: Int = 0
)

@Serializable
data class SeriesStream(
    val num: Int? = null,
    val name: String,
    @SerialName("series_id")
    val seriesId: Int,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    @SerialName("rating_5based")
    val rating5Based: Double? = null,
    @SerialName("backdrop_path")
    val backdropPath: List<String> = emptyList(),
    @SerialName("category_id")
    val categoryId: String? = null
)

@Serializable
data class ShortEpgResponse(
    @SerialName("epg_listings")
    val epgListings: List<EpgProgram> = emptyList()
)

@Serializable
data class EpgProgram(
    val id: String? = null,
    @SerialName("epg_id")
    val epgId: String? = null,
    val title: String = "",
    val lang: String = "",
    val start: String = "",
    val end: String = "",
    val description: String = "",
    @SerialName("channel_id")
    val channelId: String? = null,
    @SerialName("start_timestamp")
    val startTimestamp: String? = null,
    @SerialName("stop_timestamp")
    val stopTimestamp: String? = null,
    @SerialName("now_playing")
    val nowPlaying: Int = 0
)

enum class VideoDecoderType(val title: String) {
    HARDWARE("Hardware (HW Acelerado)"),
    SOFTWARE("Software (SW Compatibilidade)"),
    AUTO("Automático (Recomendado)")
}

data class UserSettings(
    val videoDecoder: VideoDecoderType = VideoDecoderType.AUTO,
    val bufferSizeSeconds: Int = 15,
    val streamFormat: String = "ts", // "ts" or "m3u8"
    val lastEpgUpdate: String = "Hoje, 14:30",
    val isAutoEpgUpdateEnabled: Boolean = true
)
