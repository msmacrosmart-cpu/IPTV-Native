package com.xtream.player.data

import com.example.iptv.data.model.Episode

/** Fictional IPTVnator release-demo artwork, packaged locally; streams are sample HLS. */
object DemoData {
    private const val HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private const val HLS2 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    private fun art(kind: String, slug: String) = "file:///android_asset/demo/$kind-$slug.webp"
    private const val PLOT = "Conteúdo fictício de demonstração. Conecte seu servidor Xtream para acessar sua própria biblioteca."
    val genres = listOf(Category("release", "Lançamentos"), Category("drama", "Drama"), Category("action", "Ação e mistério"), Category("scifi", "Ficção científica"), Category("docs", "Documentários"))
    private fun movie(slug: String, title: String, category: String, rating: Double, progress: Float = 0f, backdrop: Boolean = false) =
        VodItem(slug, title, "2026", rating, genres.first { it.id == category }.name, PLOT,
            art("poster", slug), if (backdrop) art("backdrop", slug) else null, category, HLS, progress = progress)
    val movies = listOf(
        movie("black-harbor", "Black Harbor", "release", 8.2, .28f, true),
        movie("summer-static", "Summer Static", "release", 8.1, .22f, true),
        movie("house-of-tides", "House of Tides", "release", 7.9, .15f, true),
        movie("field-notes", "Field Notes", "docs", 8.0, .38f),
        movie("white-room-six", "White Room Six", "scifi", 7.8, .32f),
        movie("open-late", "Open Late", "drama", 8.0, .44f),
        movie("the-paper-astronaut", "The Paper Astronaut", "scifi", 8.3, .2f),
        movie("crimson-skylark", "Crimson Skylark", "action", 8.5),
        movie("the-voltage-guard", "The Voltage Guard", "action", 8.1),
        movie("midnight-mantle", "Midnight Mantle", "action", 8.0),
        movie("quantum-comet", "Quantum Comet", "scifi", 8.7),
    )
    val series = listOf(
        VodItem("skyline-sentinels", "Skyline Sentinels", "2025", 8.5, "Drama", PLOT,
            art("poster", "skyline-sentinels"), art("backdrop", "skyline-sentinels"), "drama", null,
            seriesId = "demo:skyline-sentinels", progress = .3f),
    )
    val liveCategories = listOf(Category("news", "Notícias"), Category("sports", "Esportes"), Category("kids", "Infantil"),
        Category("docs", "Documentários"), Category("music", "Música"), Category("movies", "Filmes"))
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
            val titles = epgTitles.getValue(ch.id)
            ch.id to (0 until 10).map { i -> Programme(ch.id, titles[i % titles.size], base + i * 3_600_000L, base + (i + 1) * 3_600_000L) }
        }
    }
    fun episodes(): List<AppViewModel.EpisodeChoice> = (1..2).flatMap { season ->
        (1..4).map { number ->
            AppViewModel.EpisodeChoice(Episode("demo-$season-$number", "Episódio $number", season, number, plot = PLOT), HLS)
        }
    }
    fun content() = ContentState(liveCategories, channels, genres, movies, listOf(Category("drama", "Drama")), series)
    fun session(): Session {
        val now = System.currentTimeMillis(); val day = 86_400_000L
        return Session("demo", "demo", "demo", "Active", now + 87 * day, now - 200 * day, 1, 2, true)
    }
}
