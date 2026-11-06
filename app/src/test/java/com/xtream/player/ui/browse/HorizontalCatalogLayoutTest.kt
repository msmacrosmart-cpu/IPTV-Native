package com.xtream.player.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HorizontalCatalogLayoutTest {
    @Test fun everyItemAppearsExactlyOnceIncludingPartialFinalShelves() {
        for (rows in 1..4) for (columns in 1..10) for (count in 0..101) {
            val layout = HorizontalCatalogLayout(count, rows, columns)
            val indexes = (0 until layout.slotCount).mapNotNull(layout::itemIndex)
            assertEquals("rows=$rows columns=$columns count=$count", (0 until count).toList(), indexes.sorted())
            assertNull(layout.itemIndex(-1))
            assertNull(layout.itemIndex(layout.slotCount))
        }
    }

    @Test fun firstVisibleShelvesKeepTheOriginalRowMajorOrder() {
        val layout = HorizontalCatalogLayout(itemCount = 26, rows = 3, columns = 5)
        assertEquals(listOf(0, 1, 2, 3, 4), (0 until 5).map { layout.itemIndex(it * 3) })
        assertEquals(listOf(5, 6, 7, 8, 9), (0 until 5).map { layout.itemIndex(it * 3 + 1) })
        assertEquals(15, layout.itemIndex(15))
        assertEquals(20, layout.itemIndex(16))
        assertEquals(25, layout.itemIndex(17))
        assertNull(layout.itemIndex(20))
    }
}
