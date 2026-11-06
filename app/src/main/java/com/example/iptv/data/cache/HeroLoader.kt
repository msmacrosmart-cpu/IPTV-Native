package com.example.iptv.data.cache

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.VodDetails
import com.example.iptv.data.model.VodItem
import com.example.iptv.data.repository.XtreamRepository
import com.example.iptv.data.tmdb.TmdbRepository
import com.example.iptv.data.tmdb.TmdbResult
import com.xtream.player.data.isReleaseCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Artwork lookups are bounded to the six visible release candidates. */
data class HeroTarget(val id: Int, val title: String, val year: String = "")

private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b")

fun releaseHeroTargets(catalog: CachedCatalog): List<HeroTarget> {
    val ids = catalog.movieCategories.asSequence().filter { isReleaseCategory(it.name) }.map { it.id }.toSet()
    return catalog.movies.asSequence().filter { it.categoryId != null && ids.contains(it.categoryId) }.distinctBy(VodItem::id)
        .sortedByDescending { it.addedAtMillis ?: Long.MIN_VALUE }.take(6)
        .map { HeroTarget(it.id, it.name, YEAR.find(it.name)?.value.orEmpty()) }.toList()
}

/** Shared by foreground and WorkManager so they don't race or fetch an entire library. */
class HeroLoader(
    private val context: Context,
    private val repository: XtreamRepository,
    private val cache: CatalogCache,
    private val tmdb: () -> TmdbRepository,
    private val tmdbKey: () -> String,
) {
    private val mutex = Mutex()

    suspend fun prepare(credentials: PanelCredentials, requested: List<HeroTarget>): Boolean = mutex.withLock {
        val token = repository.sessionToken(credentials) ?: return@withLock false
        val targets = requested.distinctBy { it.id }.take(6)
        val cached = cache.loadHeroes(credentials).entries
        val now = System.currentTimeMillis()
        val apiKey = tmdbKey()
        data class Fetched(val target: HeroTarget, val hero: CachedHero?, val updated: Boolean, val failed: Boolean)
        val fetched = coroutineScope {
            targets.map { target -> async {
                val old = cached["vod:${target.id}"]
                val fresh = old != null && cacheIsFresh(old.fetchedAtMillis, now)
                if (fresh && (apiKey.isBlank() || old!!.tmdbChecked)) {
                    Fetched(target, old, false, false)
                } else {
                    var failed = false
                    val panel = if (fresh) old!!.details else when (val result = repository.vodDetails(target.id)) {
                        is ApiResult.Success -> result.data
                        is ApiResult.Failure -> { failed = true; old?.details }
                    }
                    if (panel == null) Fetched(target, old, false, failed)
                    else {
                        var checked = apiKey.isBlank()
                        var details = panel
                        if (apiKey.isNotBlank()) {
                            when (val result = tmdb().lookup(target.title, target.year, false, panel.tmdbId, apiKey)) {
                                is TmdbResult.Found -> {
                                    checked = true
                                    // Prefer TMDB's real landscape art; retain panel metadata as fallback.
                                    val extra = result.metadata.details
                                    details = panel.copy(
                                        backdrop = extra.backdrop ?: panel.backdrop,
                                        plot = panel.plot?.takeIf(String::isNotBlank) ?: extra.plot,
                                        genre = panel.genre?.takeIf(String::isNotBlank) ?: extra.genre,
                                        releaseDate = panel.releaseDate?.takeIf(String::isNotBlank) ?: extra.releaseDate,
                                        rating = panel.rating?.takeIf(String::isNotBlank) ?: extra.rating,
                                    )
                                }
                                TmdbResult.NotFound -> checked = true
                                TmdbResult.Unauthorized, TmdbResult.Unavailable -> Unit // retry later, never discard the panel
                            }
                        }
                        val updatedAt = if (fresh || failed) old?.fetchedAtMillis ?: now else System.currentTimeMillis()
                        Fetched(target, CachedHero(updatedAt, details, checked && apiKey.isNotBlank()), true, failed)
                    }
                }
            } }.awaitAll()
        }
        if (!repository.isSessionCurrent(credentials, token)) return@withLock false
        cache.mergeHeroes(credentials, fetched.filter { it.updated && it.hero != null }
            .associate { "vod:${it.target.id}" to it.hero!! }, token)
        // Never prewarm a portrait poster as a hero. UI validates actual image dimensions too.
        coroutineScope {
            fetched.mapNotNull { item ->
                item.hero?.details?.backdrop?.takeIf { it.isNotBlank() }?.let { url -> async {
                    if (repository.isSessionCurrent(credentials, token)) {
                        context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(1280, 720)
                            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.ENABLED).build())
                    }
                } }
            }.awaitAll()
        }
        fetched.none { it.failed }
    }
}
