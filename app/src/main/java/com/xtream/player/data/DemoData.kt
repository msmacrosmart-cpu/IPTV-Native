package com.xtream.player.data

object DemoData {
    private const val HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private const val HLS2 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    private const val PLOT = "Sinopse de demonstração. Conecte seu servidor Xtream para ver o catálogo real."

    val genres = listOf(
        Category("action", "Action"), Category("comedy", "Comedy"), Category("drama", "Drama"),
        Category("scifi", "Sci-Fi"), Category("thriller", "Thriller"), Category("horror", "Horror"),
        Category("documentary", "Documentary"), Category("family", "Family"), Category("fantasy", "Fantasy"),
    )

    private fun v(id: String, t: String, y: String, r: Double, cat: String, c1: Long, c2: Long, p: Float = 0f) =
        VodItem(id, t, y, r, genres.first { it.id == cat }.name, PLOT, null, null, cat, HLS, null, p, c1, c2)

    val movies = listOf(
        v("m1", "Dune: Part Two", "2024", 8.5, "scifi", 0xFFE9771B, 0xFF3B1204),
        v("m2", "The Batman", "2022", 7.8, "action", 0xFFD0561F, 0xFF1A0B08),
        v("m3", "Oppenheimer", "2023", 8.3, "drama", 0xFFB8431B, 0xFF2A0D05),
        v("m4", "Gladiator II", "2024", 7.0, "action", 0xFF7B8798, 0xFF1D2128),
        v("m5", "The Matrix", "1999", 8.7, "scifi", 0xFF2F6F4F, 0xFF0B1A12),
        v("m6", "Spirited Away", "2001", 8.6, "family", 0xFF2A2A2E, 0xFF000000),
        v("m7", "Inception", "2010", 8.8, "thriller", 0xFF2E5A8A, 0xFF0A1424),
        v("m8", "The Dark Knight", "2008", 9.0, "action", 0xFF2C4A78, 0xFF0A0F1C),
        v("m9", "Interstellar", "2014", 8.7, "scifi", 0xFF6A7580, 0xFF15181C, 0.55f),
        v("m10", "Mad Max: Fury Road", "2015", 8.1, "action", 0xFFE0A21B, 0xFF2A1B05, 0.40f),
        v("m11", "Avatar: The Way of Water", "2022", 7.6, "scifi", 0xFF1F7FB8, 0xFF081C2A, 0.30f),
        v("m12", "Spider-Man: Into the Spider-Verse", "2018", 8.4, "family", 0xFFC8283C, 0xFF2A0A12, 0.60f),
        v("m13", "Blade Runner 2049", "2017", 8.0, "scifi", 0xFFD9632B, 0xFF1C0C06, 0.70f),
        v("m14", "Operação Sombra", "2026", 7.1, "action", 0xFF6B6B72, 0xFF18181C),
        v("m15", "Guerra Fria", "2026", 7.4, "drama", 0xFF4B5563, 0xFF14171C),
        v("m16", "Último Trem", "2026", 6.9, "thriller", 0xFF8A6A4B, 0xFF1E1610),
        v("m17", "Contagem Final", "2026", 7.3, "thriller", 0xFF5B7A8C, 0xFF10181D),
        v("m18", "Última Fronteira", "2026", 7.0, "fantasy", 0xFFA4552B, 0xFF1D0E07),
    )

    val series = listOf(
        v("s1", "Game of Thrones", "2011", 9.2, "fantasy", 0xFF6B7078, 0xFF0E0F12),
        v("s2", "Breaking Bad", "2008", 9.5, "drama", 0xFFE6C619, 0xFF2A2205),
        v("s3", "Stranger Things", "2016", 8.7, "horror", 0xFFC8283C, 0xFF1A0610),
        v("s4", "The Last of Us", "2023", 8.7, "drama", 0xFF55674F, 0xFF0F150E),
        v("s5", "The Boys", "2019", 8.7, "action", 0xFFCC3B3B, 0xFF1D0B0B),
        v("s6", "Succession", "2018", 8.8, "drama", 0xFF5C7490, 0xFF10161D),
        v("s7", "Wednesday", "2022", 8.1, "comedy", 0xFF3A4A7A, 0xFF0C1020),
        v("s8", "Sherlock", "2010", 9.1, "thriller", 0xFF5A6068, 0xFF121316),
        v("s9", "Severance", "2022", 8.7, "scifi", 0xFF8AA0A0, 0xFF1A2020, 0.45f),
        v("s10", "Chernobyl", "2019", 9.3, "drama", 0xFF6A7A6A, 0xFF151A15, 0.35f),
        v("s11", "The Bear", "2022", 8.6, "comedy", 0xFFB8862B, 0xFF1F1606, 0.55f),
        v("s12", "True Detective", "2014", 8.9, "thriller", 0xFF6A6A60, 0xFF171714, 0.30f),
        v("s13", "Shōgun", "2024", 8.7, "drama", 0xFFB8383C, 0xFF200A0B, 0.65f),
        v("s14", "Peaky Blinders", "2013", 8.8, "drama", 0xFF7A7A80, 0xFF141416),
        v("s15", "The Mandalorian", "2019", 8.7, "scifi", 0xFFC08A2B, 0xFF1F1405),
        v("s16", "Dark", "2017", 8.7, "scifi", 0xFF3A5A6A, 0xFF0A1418),
        v("s17", "Fargo", "2014", 8.9, "thriller", 0xFF8A9AA8, 0xFF181C20),
        v("s18", "Arcane", "2021", 9.0, "fantasy", 0xFFC8409A, 0xFF1F0A18),
    ).map { it.copy(isSeries = true) }

    val hero = VodItem(
        "hero", "Black Harbor", "2026", 8.2, "Maritime Crime Thriller",
        "Toda maré traz um segredo. Quando um cargueiro não registrado chega a um porto sob forte neblina…",
        null, null, "thriller", HLS, null, 0f, 0xFFB5602B, 0xFF1A0E08,
    )

    val liveCategories = listOf(
        Category("news", "Notícias"), Category("sports", "Esportes"), Category("kids", "Infantil"),
        Category("docs", "Documentários"), Category("music", "Música"), Category("movies", "Filmes"),
    )

    val channels = listOf(
        Channel("c1", 1, "Aurora News", null, "news", "c1", HLS),
        Channel("c2", 2, "Fieldside Sports", null, "sports", "c2", HLS2),
        Channel("c3", 3, "Horizon Kids", null, "kids", "c3", HLS),
        Channel("c4", 4, "Atlas Docs", null, "docs", "c4", HLS2),
        Channel("c5", 5, "Night Music", null, "music", "c5", HLS),
        Channel("c6", 6, "Cinema Plus", null, "movies", "c6", HLS2),
    )

    private val epgTitles = mapOf(
        "c1" to listOf("Global Window", "Morning Briefing", "Market Watch", "World Tonight"),
        "c2" to listOf("Final Whistle", "Match Day Live", "Sports Desk", "Replay Hour"),
        "c3" to listOf("Story Lantern", "Little Explorers", "Cartoon Block", "Bedtime Tales"),
        "c4" to listOf("Wide Angle", "Deep Ocean", "Ancient Worlds", "Planet Files"),
        "c5" to listOf("Ambient Office", "Indie Mix", "Live Sessions", "Late Night Jazz"),
        "c6" to listOf("Feature Premiere", "Classic Reel", "Director's Cut", "Short Films"),
    )

    fun epg(): Map<String, List<Programme>> {
        val now = System.currentTimeMillis()
        val base = now - now % 3_600_000L - 3_600_000L
        return channels.associate { ch ->
            val t = epgTitles.getValue(ch.id)
            ch.id to (0 until 10).map { i ->
                Programme(ch.id, t[i % t.size], base + i * 3_600_000L, base + (i + 1) * 3_600_000L)
            }
        }
    }

    fun content() = ContentState(liveCategories, channels, genres, movies, genres, series, hero)

    fun session(): Session {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return Session("demo.xtream.tv", "demo", "demo", "Active", now + 87 * day, now - 200 * day, 1, 2, true)
    }
}
