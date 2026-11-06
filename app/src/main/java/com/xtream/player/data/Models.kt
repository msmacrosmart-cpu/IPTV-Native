package com.xtream.player.data

import com.example.iptv.data.model.VodDetails
import kotlinx.serialization.Serializable

data class Session(
    val server: String,
    val username: String,
    val password: String,
    val status: String,
    val expDate: Long?,
    val createdAt: Long?,
    val activeConns: Int,
    val maxConns: Int,
    val isDemo: Boolean = false,
    val epgSource: String = "",
    val isTrial: Boolean = false,
    val allowedFormats: List<String> = emptyList(),
    val timezone: String = "",
    val syncedAt: Long? = null,
)

data class Category(val id: String, val name: String)

@Serializable
data class Programme(val channelId: String, val title: String, val start: Long, val stop: Long)

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgId: String?,
    val url: String,
    val fallbackUrl: String? = null,
)

data class VodItem(
    val id: String,
    val title: String,
    val year: String,
    val rating: Double,
    val genre: String,
    val plot: String,
    val poster: String?,
    val backdrop: String?,
    val categoryId: String,
    val url: String?,
    val seriesId: String? = null,
    val progress: Float = 0f,
    val c1: Long = 0xFF3A3A40,
    val c2: Long = 0xFF111113,
    val isSeries: Boolean = seriesId != null,
    val addedAtMillis: Long? = null,
) {
    val favoriteKey: String get() = "${if (isSeries) "series" else "vod"}:$id"
}

/** Keep live progress/favorite identity while enriching the hero with the provider's artwork. */
fun VodItem.withHeroDetails(details: VodDetails?): VodItem = if (details == null) this else copy(
    plot = details.plot ?: plot,
    genre = details.genre ?: genre,
    year = details.releaseDate?.take(4) ?: year,
    backdrop = details.backdrop ?: backdrop,
    rating = details.rating?.toDoubleOrNull() ?: rating,
)

data class ContentState(
    val liveCategories: List<Category>,
    val channels: List<Channel>,
    val movieCategories: List<Category>,
    val movies: List<VodItem>,
    val seriesCategories: List<Category>,
    val series: List<VodItem>,
)

data class PlayerRequest(
    val title: String,
    val url: String,
    val isLive: Boolean,
    val playlist: List<Channel> = emptyList(),
    val index: Int = 0,
    val fallbackUrl: String? = null,
    val libraryKey: String? = null,
    val episodeId: String? = null,
    val resumePositionMs: Long = 0L,
)

fun List<Programme>?.nowNext(now: Long): Pair<Programme?, Programme?> {
    val list = this?.sortedBy { it.start } ?: return null to null
    val cur = list.firstOrNull { now in it.start until it.stop }
    val next = list.firstOrNull { it.start >= (cur?.stop ?: now) }
    return cur to next
}

fun emptyContent() = ContentState(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
