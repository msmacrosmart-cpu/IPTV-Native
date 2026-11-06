package com.xtream.player.data

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
}
