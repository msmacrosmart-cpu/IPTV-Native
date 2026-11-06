package com.xtream.player.data

import com.example.iptv.data.model.VodDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    @Test fun guideFindsCurrentAndNextEvenWhenUnsortedOrBetweenPrograms() {
        val now = Programme("ch", "Agora", 100, 200)
        val next = Programme("ch", "Depois", 250, 300)
        val programs = listOf(next, now)
        assertEquals(now to next, programs.nowNext(150))
        assertEquals(null to next, programs.nowNext(225))
        assertEquals(null to null, programs.nowNext(300))
        assertNull((null as List<Programme>?).nowNext(150).first)
    }

    @Test fun movieAndSeriesWithTheSamePanelIdHaveDifferentLibraryKeys() {
        val movie = VodItem("101", "Filme", "", 0.0, "", "", null, null, "", "http://movie")
        val series = movie.copy(title = "Série", url = null, seriesId = "101", isSeries = true)
        assertEquals("vod:101", movie.favoriteKey)
        assertEquals("series:101", series.favoriteKey)
    }

    @Test fun heroEnrichmentKeepsUpdatedProgressAndUsesTheWideBackdrop() {
        val item = VodItem("1", "Filme", "2020", 6.0, "", "", "poster", null, "", "url", progress = .42f)
        val details = VodDetails(plot = "Nova sinopse", genre = "Ação", releaseDate = "2025-01-01", backdrop = "wide", rating = "8.5")
        val enriched = item.withHeroDetails(details)
        assertEquals(.42f, enriched.progress, 0f)
        assertEquals("wide", enriched.backdrop)
        assertEquals("poster", enriched.poster)
        assertEquals("2025", enriched.year)
        assertEquals("vod:1", enriched.favoriteKey)
        assertEquals(item, item.withHeroDetails(null))
    }
}
