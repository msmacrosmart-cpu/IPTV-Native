package com.xtream.player.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

// Small, 2:3 posters like the supplied catalog screenshots. Prefer roughly five cards on
// landscape phones, and use additional columns on large TVs rather than enlarging the cards.
val CatalogGridPadding = 12.dp
val CatalogGridGap = 12.dp
const val CatalogGridFraction = .77f

fun catalogColumns(gridWidth: Dp): Int {
    val available = (gridWidth - CatalogGridPadding * 2).coerceAtLeast(0.dp)
    val atMinimumSize = ((available + CatalogGridGap) / (96.dp + CatalogGridGap)).toInt().coerceAtLeast(1)
    val atLargeSize = ceil((available + CatalogGridGap) / (148.dp + CatalogGridGap)).toInt().coerceAtLeast(1)
    return maxOf(minOf(5, atMinimumSize), minOf(atLargeSize, atMinimumSize))
}

fun catalogCardWidth(gridWidth: Dp): Dp {
    val columns = catalogColumns(gridWidth)
    return ((gridWidth - CatalogGridPadding * 2 - CatalogGridGap * (columns - 1)) / columns).coerceAtLeast(64.dp)
}
