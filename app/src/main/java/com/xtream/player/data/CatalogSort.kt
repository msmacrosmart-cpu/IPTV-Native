package com.xtream.player.data

import java.text.Collator
import java.util.Locale

/** Rating is the provider's score, not a claim about a title's actual vote count. */
enum class CatalogSort(val label: String, val defaultAscending: Boolean) {
    TITLE("De A a Z", true),
    RECENT("Últimos adicionados", false),
    RATING("Mais votados", false),
}

/** Stable ordering of the selected folder/search. Missing dates and scores stay last in
 * either direction rather than looking like the oldest or lowest-rated title. */
fun sortCatalog(items: List<VodItem>, category: String?, query: String,
                sort: CatalogSort, ascending: Boolean): List<VodItem> {
    val filtered = items.filter { (category == null || it.categoryId == category) && it.title.contains(query, ignoreCase = true) }
    val collator = Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY }
    val comparator = Comparator<VodItem> { a, b ->
        when (sort) {
            CatalogSort.TITLE -> if (ascending) collator.compare(a.title, b.title) else collator.compare(b.title, a.title)
            CatalogSort.RECENT -> compareKnown(a.addedAtMillis?.takeIf { it > 0 }, b.addedAtMillis?.takeIf { it > 0 }, ascending)
            CatalogSort.RATING -> compareKnown(a.rating.takeIf { it.isFinite() && it > 0 }, b.rating.takeIf { it.isFinite() && it > 0 }, ascending)
        }
    }
    return filtered.sortedWith(comparator)
}

private fun <T : Comparable<T>> compareKnown(a: T?, b: T?, ascending: Boolean): Int = when {
    a == null && b == null -> 0
    a == null -> 1
    b == null -> -1
    ascending -> a.compareTo(b)
    else -> b.compareTo(a)
}
