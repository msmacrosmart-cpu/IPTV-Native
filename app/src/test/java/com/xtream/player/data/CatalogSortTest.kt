package com.xtream.player.data

import com.example.iptv.data.model.SeriesItem
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.network.XtreamParser
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogSortTest {
    private fun item(id: String, title: String, category: String, added: Long?, rating: Double) =
        VodItem(id, title, "", rating, "", "", null, null, category, null, addedAtMillis = added)

    private val items = listOf(
        item("a", "Árvore", "one", 300, 8.0),
        item("b", "banana", "one", 100, 5.0),
        item("c", "Zebra", "one", null, 0.0),
        item("d", "Amora", "other", 200, 6.0),
        item("e", "Zero", "one", 0, Double.NaN),
    )

    private fun order(sort: CatalogSort, ascending: Boolean, category: String? = "one", query: String = "") =
        sortCatalog(items, category, query, sort, ascending).map { it.id }

    @Test fun alphabeticOrderingRespectsAccentsAndBothDirections() {
        assertEquals(listOf("a", "b", "c", "e"), order(CatalogSort.TITLE, true))
        assertEquals(listOf("e", "c", "b", "a"), order(CatalogSort.TITLE, false))
    }

    @Test fun datesAndRatingsReverseButUnknownValuesRemainLast() {
        assertEquals(listOf("a", "b", "c", "e"), order(CatalogSort.RECENT, false))
        assertEquals(listOf("b", "a", "c", "e"), order(CatalogSort.RECENT, true))
        assertEquals(listOf("a", "b", "c", "e"), order(CatalogSort.RATING, false))
        assertEquals(listOf("b", "a", "c", "e"), order(CatalogSort.RATING, true))
    }

    @Test fun searchAndFolderApplyBeforeSortingAndTiesAreStable() {
        assertEquals(listOf("b"), order(CatalogSort.TITLE, true, query = "ANA"))
        assertEquals(listOf("d"), order(CatalogSort.RECENT, false, category = "other"))
        val tied = listOf(items[1], items[0].copy(rating = 5.0))
        assertEquals(listOf("b", "a"), sortCatalog(tied, null, "", CatalogSort.RATING, false).map { it.id })
    }

    @Test fun seriesUseAddedDateOrPanelsLastModifiedAndOldCacheStillDecodes() {
        val json = ApiClient.json.parseToJsonElement("""[
          {"series_id":1,"name":"A","added":"1700000000","last_modified":"1800000000"},
          {"series_id":2,"name":"B","last_modified":"1700000000000"},
          {"series_id":3,"name":"C"}
        ]""")
        val series = XtreamParser.parseSeries(json)
        assertEquals(1_700_000_000_000L, series[0].addedAtMillis ?: 0L)
        assertEquals(1_700_000_000_000L, series[1].addedAtMillis ?: 0L)
        assertNull(series[2].addedAtMillis)
        val oldCache = ApiClient.json.decodeFromString<SeriesItem>("""{"id":5,"name":"Antiga"}""")
        assertNull(oldCache.addedAtMillis)
    }
}
