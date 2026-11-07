package com.xtream.player.data

import java.text.Normalizer
import java.util.Locale

/** Ignore emoji, decorative punctuation, accents and case, but not category qualifiers. */
private fun normalizedCategory(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

fun isReleaseCategory(name: String): Boolean = Regex("\\blancamentos?\\b").containsMatchIn(normalizedCategory(name))

/** Prefer the actual Lançamentos folder, not 'Infantil / Lançamentos' or themed collections.
 * Dated/quality-qualified release folders remain compatible when no unqualified folder exists.
 */
fun releaseCategoryIds(categories: List<Category>): Set<String> {
    val exact = categories.filter { normalizedCategory(it.name) in setOf("lancamento", "lancamentos") }
    if (exact.isNotEmpty()) return exact.map { it.id }.toSet()
    return categories.filter { category ->
        val words = normalizedCategory(category.name).split(' ')
        words.any { it == "lancamento" || it == "lancamentos" } && words.all {
            it in setOf("lancamento", "lancamentos", "filme", "filmes", "4k", "hd", "fhd", "uhd") || it.matches(Regex("(?:19|20)\\d{2}"))
        }
    }.map { it.id }.toSet()
}

/** Most recently added movies in the provider's release categories only.
 * Missing dates retain the provider's order and follow items with known dates.
 */
fun latestReleaseMovies(categories: List<Category>, movies: List<VodItem>, limit: Int = Int.MAX_VALUE): List<VodItem> {
    val categoryIds = releaseCategoryIds(categories)
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

/** Only successfully decoded landscape artwork may enter the eight-title carousel. */
fun validHeroSlides(items: List<VodItem>, validatedKeys: Set<String>): List<VodItem> = items.asSequence()
    .filter { !it.isSeries && !it.backdrop.isNullOrBlank() && it.favoriteKey in validatedKeys }
    .distinctBy { it.favoriteKey }.distinctBy { it.backdrop }.take(8).toList()
