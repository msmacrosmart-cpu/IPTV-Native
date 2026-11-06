package com.xtream.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlTest {
    @Test fun pinUsesSaltAndRejectsWrongValues() {
        assertFalse(ParentalPin.valid("12"))
        assertFalse(ParentalPin.valid("12ab"))
        val (salt, hash) = ParentalPin.create("1234")
        val (otherSalt, otherHash) = ParentalPin.create("1234")
        assertNotEquals(salt, otherSalt)
        assertNotEquals(hash, otherHash)
        assertTrue(ParentalPin.matches("1234", salt, hash))
        assertFalse(ParentalPin.matches("1235", salt, hash))
        assertFalse(ParentalPin.matches("1234", "invalid", hash))
    }

    @Test fun restrictionsRemoveItemsAndCategoriesEverywhereUntilUnlocked() {
        val original = ContentState(
            listOf(Category("1", "Livre"), Category("2", "Protegida")),
            listOf(Channel("1", 1, "Livre", null, "1", null, "live-1"),
                Channel("2", 2, "Protegido", null, "2", null, "live-2")),
            listOf(Category("10", "Filmes")),
            listOf(VodItem("1", "Filme", "", 0.0, "", "", null, null, "10", "movie")),
            listOf(Category("20", "Séries")),
            listOf(VodItem("1", "Série", "", 0.0, "", "", null, null, "20", null, seriesId = "1")),
        )
        val restricted = original.withParentalRestrictions(setOf("live:2", "vod:10", "series:20"), false)
        assertEquals(listOf("1"), restricted.channels.map { it.id })
        assertEquals(listOf("1"), restricted.liveCategories.map { it.id })
        assertTrue(restricted.movies.isEmpty())
        assertTrue(restricted.series.isEmpty())
        assertTrue(restricted.movieCategories.isEmpty())
        assertTrue(restricted.seriesCategories.isEmpty())
        assertEquals(original, original.withParentalRestrictions(setOf("live:2", "vod:10", "series:20"), true))
    }
}
