package com.example.iptv.data.network

import java.net.URLDecoder

/**
 * Normalises whatever the user pastes in the login screen into a Retrofit-friendly panel
 * base URL, and extracts credentials when they are embedded in the URL.
 *
 * Accepted examples:
 *  - `http://painel.com:8080`
 *  - `painel.com:8080`
 *  - `http://painel.com:8080/player_api.php`
 *  - `http://painel.com/get.php?username=user&password=pass&type=m3u_plus`
 *  - `https://painel.com/iptv/` (panels behind a sub-path)
 *  - `http://painel.com/live/user/pass/1234.ts` (a copied stream link)
 */
object PanelUrl {

    data class Parsed(
        val baseUrl: String,
        val username: String? = null,
        val password: String? = null
    ) {
        val hasCredentials: Boolean
            get() = !username.isNullOrBlank() && !password.isNullOrBlank()
    }

    private val ENDPOINT_FILES = setOf(
        "player_api.php",
        "panel_api.php",
        "get.php",
        "xmltv.php",
        "enigma2.php",
        "api.php",
        "index.php"
    )

    /** Content folders used by the Xtream streaming endpoints. */
    private val STREAM_FOLDERS = setOf("live", "movie", "series", "timeshift")

    fun normalize(rawInput: String): String = parse(rawInput).baseUrl

    fun parse(rawInput: String): Parsed {
        var input = rawInput.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (input.isEmpty()) return Parsed("")

        if (!input.startsWith("http://", true) && !input.startsWith("https://", true)) {
            input = "http://$input"
        }

        // Strip fragment first, then query string (capturing credentials on the way).
        input = input.substringBefore('#')

        var username: String? = null
        var password: String? = null
        val queryIndex = input.indexOf('?')
        if (queryIndex >= 0) {
            val query = input.substring(queryIndex + 1)
            input = input.substring(0, queryIndex)
            query.split('&').forEach { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@forEach
                val key = pair.substring(0, separator).trim().lowercase()
                val value = decode(pair.substring(separator + 1))
                when (key) {
                    "username", "user" -> if (value.isNotBlank()) username = value
                    "password", "pass" -> if (value.isNotBlank()) password = value
                }
            }
        }

        val base = buildBase(input)
        return Parsed(base, username, password)
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    private fun buildBase(url: String): String {
        val scheme: String
        val rest: String
        when {
            url.startsWith("https://", ignoreCase = true) -> {
                scheme = "https"
                rest = url.substring(8)
            }
            url.startsWith("http://", ignoreCase = true) -> {
                scheme = "http"
                rest = url.substring(7)
            }
            else -> {
                scheme = "http"
                rest = url
            }
        }

        val slashIndex = rest.indexOf('/')
        var authority = if (slashIndex >= 0) rest.substring(0, slashIndex) else rest
        val rawPath = if (slashIndex >= 0) rest.substring(slashIndex) else ""

        authority = authority.substringAfterLast('@').trim()
        authority = authority.trimEnd('/', ':')
        if (authority.isBlank()) return ""

        var segments = rawPath.split('/').filter { it.isNotBlank() }

        // Drop an API endpoint file, e.g. /player_api.php or /get.php
        if (segments.isNotEmpty()) {
            val last = segments.last().lowercase()
            val isEndpoint = last in ENDPOINT_FILES || last.endsWith(".php") || last.endsWith(".xml")
            if (isEndpoint) segments = segments.dropLast(1)
        }

        // Recover the panel root from a copied stream link: /live/user/pass/123.ts
        if (segments.size >= 4) {
            val folder = segments[segments.size - 4].lowercase()
            if (folder in STREAM_FOLDERS) {
                segments = segments.dropLast(4)
            }
        } else if (segments.size >= 3) {
            val folder = segments[segments.size - 3].lowercase()
            if (folder in STREAM_FOLDERS) {
                segments = segments.dropLast(3)
            }
        }

        val path = segments.joinToString("/")
        return buildString {
            append(scheme)
            append("://")
            append(authority)
            append('/')
            if (path.isNotEmpty()) {
                append(path)
                append('/')
            }
        }
    }
}
