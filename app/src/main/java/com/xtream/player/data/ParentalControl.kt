package com.xtream.player.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN verification uses a random per-profile salt and a deliberately slow derivation. */
object ParentalPin {
    private const val ITERATIONS = 60_000

    fun valid(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    fun create(pin: String): Pair<String, String> {
        require(valid(pin))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return salt.hex() to derive(pin, salt).hex()
    }

    fun matches(pin: String, salt: String, hash: String): Boolean = runCatching {
        valid(pin) && MessageDigest.isEqual(derive(pin, salt.unhex()), hash.unhex())
    }.getOrDefault(false)

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun String.unhex(): ByteArray {
        require(length % 2 == 0 && isNotEmpty())
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/** Filter before Home, search and live TV so restricted content cannot leak there. */
fun ContentState.withParentalRestrictions(blocked: Set<String>, unlocked: Boolean): ContentState {
    if (unlocked || blocked.isEmpty()) return this
    return copy(
        liveCategories = liveCategories.filterNot { "live:${it.id}" in blocked },
        channels = channels.filterNot { "live:${it.categoryId}" in blocked },
        movieCategories = movieCategories.filterNot { "vod:${it.id}" in blocked },
        movies = movies.filterNot { "vod:${it.categoryId}" in blocked },
        seriesCategories = seriesCategories.filterNot { "series:${it.id}" in blocked },
        series = series.filterNot { "series:${it.categoryId}" in blocked },
    )
}
