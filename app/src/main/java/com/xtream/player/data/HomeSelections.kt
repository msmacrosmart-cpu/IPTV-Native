package com.xtream.player.data

import java.text.Normalizer
import java.util.Locale

/** Panels often decorate category names with emoji, years or different accents/capitalization. */
fun isReleaseCategory(name: String): Boolean {
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    return normalized.contains("lancamento")
}

/** Most recently added movies in the provider's release categories only.
 * Missing dates retain the provider's order and follow items with known dates.
 */
fun latestReleaseMovies(categories: List<Category>, movies: List<VodItem>, limit: Int = 6): List<VodItem> {
    val categoryIds = categories.asSequence().filter { isReleaseCategory(it.name) }.map { it.id }.toSet()
    if (categoryIds.isEmpty()) return emptyList()
    return movies.asSequence().filter { !it.isSeries && it.categoryId in categoryIds }
        .distinctBy { it.favoriteKey }
        .sortedByDescending { it.addedAtMillis ?: Long.MIN_VALUE }
        .take(limit.coerceAtLeast(0)).toList()
}

/** Ranking is the score supplied by the panel, not a claim about views or popularity. */
fun topRated(items: List<VodItem>, limit: Int = 10): List<VodItem> = items.asSequence()
    .distinctBy { it.favoriteKey }
    .sortedByDescending { it.rating.takeIf { score -> score.isFinite() } ?: 0.0 }
    .take(limit.coerceAtLeast(0)).toList()
