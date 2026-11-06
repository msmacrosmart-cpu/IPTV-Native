package com.xtream.player.ui.browse

/**
 * LazyHorizontalGrid fills columns first, but the original catalog is ordered in rows.
 * Map each screen-sized group into columns so the first visible shelves keep that order.
 * Empty slots in the final group are non-focusable and never duplicate a title.
 */
internal class HorizontalCatalogLayout(itemCount: Int, rows: Int, columns: Int) {
    private val count = itemCount.coerceAtLeast(0)
    private val rows = rows.coerceAtLeast(1)
    private val columns = columns.coerceAtLeast(1)
    private val groupSize = this.rows * this.columns
    private val fullGroups = count / groupSize
    private val remainder = count % groupSize
    val slotCount: Int = (fullGroups * this.columns + minOf(this.columns, remainder)) * this.rows

    fun itemIndex(slot: Int): Int? {
        if (slot !in 0 until slotCount) return null
        val column = slot / rows
        val row = slot % rows
        val index = (column / columns) * groupSize + row * columns + column % columns
        return index.takeIf { it < count }
    }
}
