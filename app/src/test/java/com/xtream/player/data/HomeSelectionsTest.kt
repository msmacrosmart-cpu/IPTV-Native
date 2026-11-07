package com.xtream.player.data

import com.example.iptv.data.cache.CATALOG_TTL_MILLIS
import com.example.iptv.data.cache.CachedCatalog
import com.example.iptv.data.cache.CatalogCache
import com.example.iptv.data.cache.cacheIsFresh
import com.example.iptv.data.cache.releaseHeroTargets
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.model.VodCategory
import com.example.iptv.data.model.VodItem as PanelMovie
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.network.XtreamParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSelectionsTest {
    private fun movie(id: String, category: String, added: Long?, rating: Double = 0.0) =
        VodItem(id, "Filme $id", "", rating, "", "", null, null, category, "url", addedAtMillis = added)

    @Test fun heroUsesOnlyReleaseMoviesAndSortsDatesWithoutDisturbingMissingDates() {
        val cats = listOf(Category("old", "Ação"), Category("new", "🔥 LANÇAMENTOS 2026"), Category("more", "Lançamento 4K"))
        val items = listOf(movie("x", "old", 9999), movie("a", "new", null), movie("b", "new", 100),
            movie("c", "more", 300), movie("d", "new", null), movie("e", "new", 200),
            movie("series", "new", 999).copy(isSeries = true))
        assertEquals(listOf("c", "e", "b", "a", "d"), latestReleaseMovies(cats, items).map { it.id })
        assertTrue(latestReleaseMovies(listOf(Category("old", "Drama")), items).isEmpty())
        assertTrue(isReleaseCategory("LANÇAMENTO - 4K"))
        assertFalse(isReleaseCategory("Destaques"))
        val raw = CachedCatalog("key", movieCategories = cats.map { VodCategory(it.id, it.name) }, movies = listOf(
            PanelMovie(10, "a", categoryId = "new", addedAtMillis = 10),
            PanelMovie(11, "b", categoryId = "old", addedAtMillis = 100),
            PanelMovie(12, "c", categoryId = "more", addedAtMillis = 20)))
        assertEquals(listOf(12, 10), releaseHeroTargets(raw).map { it.id })
    }

    @Test fun emojiMainReleaseFolderWinsOverChildrensAndThemedReleaseFolders() {
        val categories = listOf(Category("kids", "👶 Lançamentos Infantis"),
            Category("main", "🎬🔥 LANÇAMENTOS 🔥"), Category("dated", "Lançamentos 2026"))
        assertEquals(setOf("main"), releaseCategoryIds(categories))
        assertTrue(releaseCategoryIds(listOf(Category("kids", "Infantil | Lançamentos"))).isEmpty())
        val movies = listOf(movie("child", "kids", 9999), movie("release", "main", 1), movie("other", "dated", 999))
        assertEquals(listOf("release"), latestReleaseMovies(categories, movies).map { it.id })
        val many = (1..100).map { movie(it.toString(), "main", it.toLong()) }
        assertEquals(100, latestReleaseMovies(categories, many).size)
        val raw = CachedCatalog("key", movieCategories = categories.map { VodCategory(it.id, it.name) },
            movies = listOf(PanelMovie(1, "Child", categoryId = "kids"), PanelMovie(2, "Release", categoryId = "main")))
        assertEquals(listOf(2), releaseHeroTargets(raw).map { it.id })
    }

    @Test fun heroRequiresValidatedBackdropDeduplicatesAndNeverExceedsEight() {
        val items = (1..14).map { movie(it.toString(), "new", it.toLong()).copy(backdrop = "https://example.com/$it.jpg") }
        val mixed = listOf(items[0], items[0], items[1].copy(backdrop = null),
            items[2].copy(isSeries = true)) + items.drop(3)
        val approved = items.filter { it.id != "4" }.map { it.favoriteKey }.toSet()
        val result = validHeroSlides(mixed, approved)
        assertEquals(8, result.size)
        assertEquals(result.size, result.distinctBy { it.favoriteKey }.size)
        assertFalse(result.any { it.id in setOf("2", "3", "4") })
        assertTrue(validHeroSlides(items, emptySet()).isEmpty())
        assertEquals(1, validHeroSlides(items.map { it.copy(backdrop = "https://example.com/reused.jpg") }, approved).size)
    }

    @Test fun childrensMoviesAreExcludedEvenInsideTheMainReleaseFolder() {
        assertTrue(isChildrensHero("Novo filme", "Animação, Família"))
        assertTrue(isChildrensHero("Novo filme", "Children / Animation"))
        assertTrue(isChildrensHero("Sessão Infantil", "Aventura"))
        assertFalse(isChildrensHero("Lançamento de ação", "Ação / Suspense"))
        val items = listOf(movie("kids", "main", 10).copy(backdrop = "kids.jpg", genre = "Família"),
            movie("movie", "main", 9).copy(backdrop = "film.jpg", genre = "Drama"))
        assertEquals(listOf("movie"), validHeroSlides(items, items.map { it.favoriteKey }.toSet()).map { it.id })
    }

    @Test fun topTenUsesProviderScoresAndStableTiesRatherThanClaimingPopularity() {
        val items = (1..12).map { movie(it.toString(), "a", null, rating = it.toDouble()) }
        assertEquals((12 downTo 3).map(Int::toString), topRated(items).map { it.id })
        assertEquals(listOf("first", "second"), topRated(listOf(movie("first", "a", null, 8.0), movie("second", "a", null, 8.0))).map { it.id })
    }

    @Test fun cacheExpiresAtThreeDaysAndAccountsAreSeparate() {
        val now = 2_000_000_000_000L
        assertTrue(cacheIsFresh(now - CATALOG_TTL_MILLIS + 1, now))
        assertFalse(cacheIsFresh(now - CATALOG_TTL_MILLIS, now))
        assertFalse(cacheIsFresh(now + 1, now))
        val day = 24L * 60 * 60 * 1000
        assertFalse(cacheIsFresh(now - day, now, intervalDays = 1))
        assertTrue(cacheIsFresh(now - day, now, intervalDays = 2))
        assertFalse(cacheIsFresh(now - 2 * day, now, intervalDays = 2))
        assertTrue(cacheIsFresh(now - 2 * day, now, intervalDays = 3))
        assertEquals(24, com.example.iptv.data.model.UserSettings().epgIntervalHours)
        assertEquals(3, com.example.iptv.data.model.UserSettings().catalogIntervalDays)
        assertFalse(CachedCatalog("key", now, complete = false).isFresh(now))
        val one = PanelCredentials("http://example.com/", "alice", "one")
        assertNotEquals(CatalogCache.accountKey(one), CatalogCache.accountKey(one.copy(username = "bob")))
        assertNotEquals(CatalogCache.accountKey(one), CatalogCache.accountKey(one.copy(serverUrl = "http://another.example/")))
        assertEquals(CatalogCache.accountKey(one), CatalogCache.accountKey(one.copy(password = "changed")))
    }

    @Test fun panelDatesAcceptSecondsAndMilliseconds() {
        val json = ApiClient.json.parseToJsonElement("""[{"stream_id":1,"name":"A","added":"1700000000"},{"stream_id":2,"name":"B","added":"1700000000000"}]""")
        val movies = XtreamParser.parseVodStreams(json)
        assertEquals(1_700_000_000_000L, movies[0].addedAtMillis ?: 0L)
        assertEquals(1_700_000_000_000L, movies[1].addedAtMillis ?: 0L)
    }
}
