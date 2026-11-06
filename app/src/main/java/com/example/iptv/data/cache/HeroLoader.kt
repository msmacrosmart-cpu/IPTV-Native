package com.example.iptv.data.cache

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.VodItem
import com.example.iptv.data.repository.XtreamRepository
import com.xtream.player.data.isReleaseCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only these six images are prewarmed; visible posters/logos use Coil's bounded LRU cache. */
data class HeroTarget(val id: Int, val poster: String?)

fun releaseHeroTargets(catalog: CachedCatalog): List<HeroTarget> {
    val ids = catalog.movieCategories.asSequence().filter { isReleaseCategory(it.name) }.map { it.id }.toSet()
    return catalog.movies.asSequence().filter { it.categoryId != null && ids.contains(it.categoryId) }.distinctBy(VodItem::id)
        .sortedByDescending { it.addedAtMillis ?: Long.MIN_VALUE }.take(6).map { HeroTarget(it.id, it.icon) }.toList()
}

/** Shared by foreground and WorkManager so they don't race or fetch hundreds of backdrops. */
class HeroLoader(
    private val context: Context,
    private val repository: XtreamRepository,
    private val cache: CatalogCache,
) {
    private val mutex = Mutex()

    suspend fun prepare(credentials: PanelCredentials, requested: List<HeroTarget>): Boolean = mutex.withLock {
        val token = repository.sessionToken(credentials) ?: return@withLock false
        val targets = requested.distinctBy { it.id }.take(6)
        val cached = cache.loadHeroes(credentials).entries
        val now = System.currentTimeMillis()
        data class Fetched(val target: HeroTarget, val hero: CachedHero?, val updated: Boolean, val failed: Boolean)
        val fetched = coroutineScope {
            targets.map { target -> async {
                val old = cached["vod:${target.id}"]
                if (old != null && cacheIsFresh(old.fetchedAtMillis, now)) Fetched(target, old, false, false)
                else when (val result = repository.vodDetails(target.id)) {
                    is ApiResult.Success -> Fetched(target, CachedHero(System.currentTimeMillis(), result.data), true, false)
                    is ApiResult.Failure -> Fetched(target, old, false, true)
                }
            } }.awaitAll()
        }
        if (!repository.isSessionCurrent(credentials, token)) return@withLock false
        cache.mergeHeroes(credentials, fetched.filter { it.updated && it.hero != null }
            .associate { "vod:${it.target.id}" to it.hero!! }, token)
        // A disk hit avoids a network request; misses download at most the six hero images.
        coroutineScope {
            fetched.mapNotNull { item ->
                val image = item.hero?.details?.backdrop?.takeIf { it.isNotBlank() }
                    ?: item.target.poster?.takeIf { it.isNotBlank() }
                image?.let { url -> async {
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
