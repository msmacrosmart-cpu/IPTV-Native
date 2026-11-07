package com.example.iptv.data.cache

import android.content.Context
import coil.imageLoader
import coil.request.SuccessResult
import coil.request.ImageRequest
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.VodDetails
import com.example.iptv.data.model.VodItem
import com.example.iptv.data.repository.XtreamRepository
import com.example.iptv.data.tmdb.TmdbRepository
import com.example.iptv.data.tmdb.TmdbResult
import com.xtream.player.data.Category
import com.xtream.player.data.releaseCategoryIds
import com.xtream.player.data.isChildrensHero
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Release candidates are scanned in small batches until eight landscape banners are found. */
data class HeroTarget(val id: Int, val title: String, val year: String = "", val backdrop: String? = null)

private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b")

fun releaseHeroTargets(catalog: CachedCatalog): List<HeroTarget> {
    val ids = releaseCategoryIds(catalog.movieCategories.map { Category(it.id, it.name) })
    return catalog.movies.asSequence().filter { it.categoryId != null && ids.contains(it.categoryId) }.distinctBy(VodItem::id)
        .sortedByDescending { it.addedAtMillis ?: Long.MIN_VALUE }
        .map { HeroTarget(it.id, it.name, YEAR.find(it.name)?.value.orEmpty(), it.backdrop) }.toList()
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
        val targets = requested.distinctBy { it.id }
        val cached = cache.loadHeroes(credentials).entries
        val now = System.currentTimeMillis()
        val apiKey = tmdbKey()
        data class Fetched(val target: HeroTarget, val hero: CachedHero, val failed: Boolean)
        val selected = linkedMapOf<String, CachedHero>()
        val artwork = mutableSetOf<String>()
        var anyFailure = false
        val slots = Semaphore(4)
        for (batch in targets.chunked(8)) {
            if (!repository.isSessionCurrent(credentials, token)) return@withLock false
            val fetched = coroutineScope {
            batch.map { target -> async { slots.withPermit {
                val old = cached["vod:${target.id}"]
                val fresh = old != null && cacheIsFresh(old.fetchedAtMillis, now)
                if (fresh && (apiKey.isBlank() || old!!.tmdbChecked)) {
                    Fetched(target, old!!, false)
                } else {
                    var failed = false
                    val provider = if (fresh) old!!.details else when (val result = repository.vodDetails(target.id)) {
                        is ApiResult.Success -> result.data
                        is ApiResult.Failure -> { failed = true; old?.details }
                    }
                    // A failed details endpoint must not erase catalog artwork or prevent a
                    // title-specific TMDB fallback. Never reuse another target's metadata.
                    val panel = (provider ?: VodDetails()).let {
                        it.copy(backdrop = it.backdrop?.takeIf(String::isNotBlank) ?: target.backdrop)
                    }
                    run {
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
                        // Do not mark a transient details failure as fresh for three days.
                        val updatedAt = if (failed) old?.fetchedAtMillis ?: 0L
                            else if (fresh) old!!.fetchedAtMillis else System.currentTimeMillis()
                        Fetched(target, CachedHero(updatedAt, details, checked && apiKey.isNotBlank()), failed)
                    }
                }
            } } }.awaitAll()
        }
            anyFailure = anyFailure || fetched.any { it.failed }
            val validated = coroutineScope {
                fetched.map { entry -> async { slots.withPermit {
                    val excluded = isChildrensHero(entry.target.title, entry.hero.details.genre.orEmpty())
                    val url = entry.hero.details.backdrop?.takeIf { it.isNotBlank() && !excluded }
                    val result = url?.let { context.imageLoader.execute(ImageRequest.Builder(context)
                        .data(it).size(1280, 720).build()) as? SuccessResult }
                    val image = result?.drawable
                    entry.takeIf { image != null && image.intrinsicHeight > 0 && image.intrinsicWidth > image.intrinsicHeight }
                } } }.awaitAll().filterNotNull()
            }
            for (entry in validated) {
                if (selected.size == 8) break
                if (artwork.add(entry.hero.details.backdrop!!)) selected["vod:${entry.target.id}"] = entry.hero
            }
            // Publish each batch so a slow/missing image cannot hold the entire carousel hostage.
            // Only decoded landscape artwork is cached as a selected hero.
            if (!repository.isSessionCurrent(credentials, token)) return@withLock false
            cache.mergeHeroes(credentials, selected, token)
            if (selected.size >= 8) break
        }
        !anyFailure
    }
}
