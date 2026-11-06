package com.xtream.player.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSizingTest {
    @Test fun landscapePhoneShowsAboutFiveCompactPostersAndTvShowsMore() {
        val phoneGrid = 566.dp
        val tvGrid = 1010.dp
        assertEquals(5, catalogColumns(phoneGrid))
        assertTrue(catalogCardWidth(phoneGrid) < 120.dp)
        assertTrue(catalogColumns(tvGrid) > 5)
        assertTrue(catalogCardWidth(tvGrid) <= 160.dp)
    }
}
