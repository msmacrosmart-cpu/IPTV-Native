package com.example.iptv

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.ui.PlayerView
import androidx.work.WorkManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptv.data.cache.BackgroundSync
import com.example.iptv.data.cache.CatalogCache
import com.example.iptv.data.epg.XmltvRepository
import com.example.iptv.data.model.ApiResult
import com.example.iptv.data.model.PanelCredentials
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.tmdb.TmdbRepository
import com.xtream.player.data.AppViewModel
import com.xtream.player.data.UserLibrary
import coil.imageLoader
import coil.decode.DataSource
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/** Real HTTP panel, XMLTV, H.264 transport stream and Compose/D-pad interactions. */
@RunWith(AndroidJUnit4::class)
class ScreenIntegrationTest {
    @get:Rule(order = 0)
    val cleanSession = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IPTVApplication
            app.repository.logout()
            app.getSharedPreferences("iptv_native_profiles", Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("xtream_library", Context.MODE_PRIVATE).edit().clear().commit()
            app.epgRepository.clear()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 2)
    val diagnostics = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            runCatching { capture("ui-failure.png") }
            runCatching {
                val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
                val nodes = compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes().joinToString("\n") { "${it.boundsInRoot} ${it.config}" }
                val ime = readShell("dumpsys input_method").lineSequence().filter {
                    it.contains("mInputShown") || it.contains("mShowRequested") || it.contains("mCurFocusedWindow") ||
                        it.contains("mCurClient") || it.contains("mCurMethodId") || it.contains("mServedView")
                }.joinToString("\n")
                val safeBounds = runCatching { compose.onNodeWithTag("device-safe-content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.getOrNull()
                val loginBounds = runCatching { compose.onNodeWithTag("login-panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.getOrNull()
                val decor = compose.activity.window.decorView
                val text = "view=${decor.width}x${decor.height} safe=$safeBounds login=$loginBounds " +
                    "session=${vm.session?.username} channels=${vm.content.channels.size} " +
                    "movies=${vm.content.movies.size} series=${vm.content.series.size} " +
                    "favorites=${vm.favorites} streams=$streamPaths epg=${vm.epg.keys} volumes=${multiVolumes()} " +
                    "windowFocus=${compose.activity.window.decorView.hasWindowFocus()} currentFocus=${compose.activity.currentFocus?.javaClass?.simpleName} " +
                    "imeInsets=${keyboardIsVisible()}\n${imeTrace.joinToString("\n")}\n$ime\n$nodes"
                writeHostFile("ui-failure.txt", text.toByteArray())
                writeHostFile("ui-failure-${description.methodName}.txt", text.toByteArray())
            }
        }
    }
    private lateinit var server: MockWebServer
    private val streamPaths = CopyOnWriteArrayList<String>()
    private val imeTrace = CopyOnWriteArrayList<String>()
    private val vodInfoIds = CopyOnWriteArrayList<String>()
    private val tmdbRequests = CopyOnWriteArrayList<String>()
    private val guideRequests = AtomicInteger()
    private val shortRequests = AtomicInteger()
    private val catalogueRequests = AtomicInteger()
    private val moviesCount = AtomicInteger(30)
    private val seriesCount = AtomicInteger(1)
    private val longGuide = AtomicBoolean(false)
    private val offline = AtomicBoolean(false)
    private val failVodStreams = AtomicBoolean(false)
    private val blockNextVod = AtomicBoolean(false)
    private val vodBlocked = CountDownLatch(1)
    private val releaseVod = CountDownLatch(1)
    private val blockStartupStages = AtomicBoolean(false)
    private val stageActions = listOf("get_live_categories", "get_live_streams", "get_vod_streams", "get_series")
    private val stageReached = stageActions.associateWith { CountDownLatch(1) }
    private val stageRelease = stageActions.associateWith { CountDownLatch(1) }
    private val streamAgents = CopyOnWriteArrayList<String>()
    private val video by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("test-pattern.ts").use { it.readBytes() }
    }

    @Before fun startPanel() {
        server = MockWebServer().apply { start() }
        // Never call synchronized server.url() inside a dispatcher: shutdown() holds the same
        // monitor while waiting for dispatchers to finish, which can otherwise deadlock.
        val panelBase = server.url("/")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                respond(request).setHeader("Connection", "close")

            private fun respond(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath
                val action = url.queryParameter("action")
                if (action in setOf("get_live_categories", "get_live_streams", "get_vod_categories", "get_vod_streams", "get_series_categories", "get_series"))
                    catalogueRequests.incrementAndGet()
                if (offline.get()) return MockResponse().setResponseCode(503)
                if (blockStartupStages.get() && action != null && action in stageActions) {
                    stageReached.getValue(action).countDown()
                    if (!stageRelease.getValue(action).await(40, TimeUnit.SECONDS)) return MockResponse().setResponseCode(503)
                }
                if (action == "get_vod_streams" && failVodStreams.get())
                    return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"error":"temporarily unavailable"}""")
                if (action == "get_vod_streams" && blockNextVod.compareAndSet(true, false)) {
                    vodBlocked.countDown()
                    if (!releaseVod.await(15, TimeUnit.SECONDS)) return MockResponse().setResponseCode(503)
                }
                if (path.startsWith("/tmdb/3/")) {
                    tmdbRequests.add(request.path.orEmpty())
                    if (url.queryParameter("api_key") != "mock-key") return MockResponse().setResponseCode(401)
                    val details = """{"overview":"Sinopse do TMDB","release_date":"2024-01-01","vote_average":8.2,
                        "genres":[{"name":"Aventura"}],"credits":{"cast":[
                        {"name":"Ator do TMDB","character":"Protagonista","profile_path":"/ator.jpg"},
                        {"name":"Atriz do TMDB","character":"Amiga"},
                        {"name":"Ator 3"},{"name":"Ator 4"},{"name":"Ator 5"},
                        {"name":"Ator 6"},{"name":"Ator 7"},{"name":"Ator 8"}]},
                        "videos":{"results":[{"site":"YouTube","type":"Trailer","official":true,"key":"abcdefghijk"}]}}"""
                    return if (path == "/tmdb/3/movie/42" || path == "/tmdb/3/tv/77")
                        MockResponse().setHeader("Content-Type", "application/json").setBody(details)
                    else MockResponse().setResponseCode(404)
                }
                if (path.endsWith("/xmltv.php")) {
                    guideRequests.incrementAndGet()
                    return MockResponse().setHeader("Content-Type", "application/xml").setBody(guide())
                }
                if (path.startsWith("/art/")) {
                    val name = path.substringAfterLast('/')
                    val bytes = InstrumentationRegistry.getInstrumentation().targetContext.assets.open("demo/$name").use { it.readBytes() }
                    return MockResponse().setHeader("Content-Type", "image/webp").setHeader("Cache-Control", "no-store").setBody(Buffer().write(bytes))
                }
                if (path.startsWith("/live/") || path.startsWith("/movie/") || path.startsWith("/series/")) {
                    streamPaths.add(path)
                    streamAgents.add(request.getHeader("User-Agent").orEmpty())
                    return MockResponse().setHeader("Content-Type", "video/mp2t").setBody(Buffer().write(video))
                }
                if (!path.endsWith("/player_api.php")) return MockResponse().setResponseCode(404)
                val body = when (url.queryParameter("action")) {
                    null -> """{"user_info":{"username":"alice","auth":1,"status":"Active","exp_date":"1900000000","created_at":"1700000000","max_connections":"2","active_cons":"1"}}"""
                    "get_live_categories" -> """[{"category_id":"1","category_name":"Notícias"},{"category_id":"2","category_name":"Esportes"}]"""
                    "get_live_streams" -> """[{"stream_id":1,"num":1,"name":"Canal Notícias","category_id":"1","epg_channel_id":"news"},{"stream_id":2,"num":2,"name":"Canal Esportes","category_id":"2","epg_channel_id":"sports"}]"""
                    "get_short_epg" -> {
                        shortRequests.incrementAndGet()
                        val title = Base64.getEncoder().encodeToString("Esporte ao vivo".toByteArray())
                        val now = System.currentTimeMillis() / 1000
                        """{"epg_listings":[{"id":"1","title":"$title","start_timestamp":"${now - 60}","stop_timestamp":"${now + 3600}"}]}"""
                    }
                    "get_vod_categories" -> """[{"category_id":"10","category_name":"Lançamentos"},{"category_id":"11","category_name":"Ação"}]"""
                    "get_vod_streams" -> {
                        val art = listOf("black-harbor", "summer-static", "house-of-tides", "field-notes", "white-room-six", "open-late", "crimson-skylark", "the-voltage-guard")
                        (101 until 101 + moviesCount.get()).joinToString(prefix = "[", postfix = "]") { id ->
                            val name = if (id == 101) "Filme Teste" else "Filme $id"
                            val poster = panelBase.resolve("/art/poster-${art[(id - 101) % art.size]}.webp")
                            """{"stream_id":$id,"name":"$name","category_id":"10","container_extension":"ts","rating":"8.0","added":"${2_000_000_000L - id}","stream_icon":"$poster"}"""
                        }
                    }
                    "get_vod_info" -> {
                        val id = url.queryParameter("vod_id").orEmpty()
                        vodInfoIds.add(id)
                        // One item deliberately has no backdrop, to exercise the sharp poster fallback.
                        if (id == "102") """{"info":{"plot":"Sem arte larga"}}"""
                        else """{"info":{"plot":"Sinopse real","genre":"Ação","cast":"Ator do painel, Atriz do painel","tmdb_id":"42","youtube_trailer":"abcdefghijk","backdrop_path":["${panelBase.resolve("/art/backdrop-black-harbor.webp")}"]}}"""
                    }
                    "get_series_categories" -> """[{"category_id":"20","category_name":"Drama"}]"""
                    "get_series" -> (101 until 101 + seriesCount.get()).joinToString(prefix = "[", postfix = "]") { id ->
                        val name = if (id == 101) "Série Teste" else "Série $id"
                        """{"series_id":$id,"name":"$name","category_id":"20","genre":"Drama","cover":"${panelBase.resolve("/art/poster-skyline-sentinels.webp")}"}"""
                    }
                    "get_series_info" -> """{"info":{"plot":"Sinopse da série","cast":"Pessoa da série","tmdb_id":"77","youtube_trailer":"abcdefghijk","backdrop_path":["${panelBase.resolve("/art/backdrop-skyline-sentinels.webp")}"]},"episodes":{"1":[{"id":"201","episode_num":1,"season":1,"title":"Primeiro","container_extension":"ts"},{"id":"202","episode_num":2,"season":1,"title":"Segundo","container_extension":"ts"}]}}"""
                    else -> "{}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
    }

    @After fun stopPanel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IPTVApplication
        app.tmdbRepository = TmdbRepository(ApiClient.httpClient())
        releaseVod.countDown()
        stageRelease.values.forEach { it.countDown() }
        server.shutdown()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun nativeScreensUseRealSessionCatalogFavoritesEpgAndPlayback() {
        compose.waitUntil(10_000) { existsUnmerged("app-background") }
        compose.onNodeWithTag("app-background", useUnmergedTree = true).assertExists()
        compose.waitUntil(30_000) { existsUnmerged("app-background-loaded") }
        assertLoginFitsAndIsCentered()
        capture("screen-login.png")
        val loginFieldWidth = compose.onNodeWithText("Servidor Xtream").fetchSemanticsNode().boundsInRoot.width
        assertTrue("The login form should stay compact on TV", loginFieldWidth <= 500f * compose.activity.resources.displayMetrics.density)
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("TV").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.channels.size == 2 && vm.content.movies.size == 30 && vm.content.series.size == 1 }
        val app = compose.activity.application as IPTVApplication
        assertEquals(1_900_000_000_000L, app.repository.account.value?.expiresAtMillis ?: 0L)
        compose.waitUntil(30_000) { app.epgRepository.guide.value.programCount == 1 }
        compose.onNodeWithTag("app-background", useUnmergedTree = true).assertExists()
        compose.waitUntil(30_000) { vodInfoIds.containsAll(listOf("101", "102", "103", "104", "105", "106")) }
        useRemoteInputMode()
        compose.onNodeWithContentDescription("Destaque 1: Filme Teste").performClick()
        compose.waitUntil(10_000) { exists("hero-watch-vod:101") }
        compose.onNodeWithTag("hero-watch-vod:101").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(30_000) { existsUnmerged("hero-backdrop-loaded-vod:101") }
        compose.onNodeWithTag("hero-details-vod:101").assertIsDisplayed()
        val watchBounds = compose.onNodeWithTag("hero-watch-vod:101").fetchSemanticsNode().boundsInRoot
        val detailsBounds = compose.onNodeWithTag("hero-details-vod:101").fetchSemanticsNode().boundsInRoot
        val heroBounds = compose.onNodeWithTag("hero-carousel").fetchSemanticsNode().boundsInRoot
        assertTrue("Hero actions must not overlap", watchBounds.right < detailsBounds.left)
        assertTrue("The hero must extend across the content pane", heroBounds.width > watchBounds.width * 3)
        capture("screen-home.png")
        compose.onNodeWithTag("hero-watch-vod:101").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("hero-details-vod:101").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("hero-details-vod:101").performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("hero-details-vod:102").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("hero-details-vod:102").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("hero-watch-vod:102").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("hero-watch-vod:102").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("hero-watch-vod:101").assertIsFocused() }.isSuccess }
        listOf("Continuar assistindo", "Canais favoritos ao vivo", "Filmes e séries favoritos").forEach { title ->
            assertTrue("Empty rail must be completely absent: $title", runCatching {
                compose.onNodeWithTag("home-list").performScrollToNode(hasText(title))
            }.isFailure)
        }
        // Two separate top-ten rails are ranked by panel score; the playlist block is gone.
        assertFalse(textExists("Xtream · 2 canais"))
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Top 10 filmes · por nota do provedor"))
        compose.onNodeWithTag("top-movies").assertIsDisplayed()
        capture("screen-top-10.png")
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Top 10 séries · por nota do provedor"))
        compose.onNodeWithTag("top-series").assertIsDisplayed()
        compose.onNodeWithTag("home-list").performScrollToIndex(0)
        listOf("Home", "TV", "Filmes", "Séries", "Multitela", "Configurações").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
        // Assistir on the hero plays a movie directly without going through details.
        compose.onNodeWithContentDescription("Destaque 1: Filme Teste").performClick()
        compose.waitUntil(10_000) { exists("hero-watch-vod:101") }
        compose.onNodeWithTag("hero-watch-vod:101").performClick()
        compose.waitUntil(30_000) { streamPaths.any { it.contains("/movie/") && it.endsWith("/101.ts") } && playerState() == "playing" }
        showControls()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.runOnIdle { vm.saveProgress(com.xtream.player.data.PlayerRequest("Filme Teste", "unused", false, libraryKey = "vod:101"), 20_000, 100_000) }
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Continuar assistindo"))
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        val homeCardWidth = compose.onAllNodesWithTag("poster-vod:101").fetchSemanticsNodes().first().boundsInRoot.width

        // Movie and series may have the same numerical id; pagination and favorite keys stay distinct.
        compose.onNodeWithContentDescription("Filmes").performClick()
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        assertSideBySide("browse-categories-pane", "catalog-grid")
        val catalogCardWidth = compose.onNodeWithTag("poster-vod:101").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Home and catalog must use equally compact posters", kotlin.math.abs(homeCardWidth - catalogCardWidth) <= 2f * compose.activity.resources.displayMetrics.density)
        assertTrue("Posters should remain compact on a TV", catalogCardWidth <= 160f * compose.activity.resources.displayMetrics.density)
        compose.waitUntil(30_000) { existsUnmerged("loaded-poster-vod:101") }
        capture("screen-movies.png")
        compose.onNodeWithTag("category-search").performTextInput("não existe")
        compose.waitUntil(10_000) { textExists("Nenhuma categoria encontrada.") }
        compose.onNodeWithTag("category-search").performTextClearance()
        compose.onNodeWithTag("category-search").performImeAction()
        compose.waitUntil(10_000) { textExists("Ação") && !keyboardIsVisible() }
        compose.onNodeWithContentDescription("Próxima página").assertDoesNotExist()
        compose.onNodeWithTag("catalog-grid").performScrollToNode(hasTestTag("poster-vod:125"))
        compose.waitUntil(10_000) { exists("poster-vod:125") }
        compose.onNodeWithTag("catalog-grid").performScrollToIndex(0)
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        compose.onNodeWithTag("search-field").performTextInput("inexistente")
        compose.waitUntil(10_000) { !exists("poster-vod:101") }
        compose.onNodeWithTag("search-field").performTextClearance()
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        compose.onNodeWithTag("poster-vod:101").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) { existsUnmerged("favorite-icon-vod:101") }
        compose.onNodeWithContentDescription("Séries").performClick()
        compose.waitUntil(10_000) { exists("poster-series:101") }
        compose.onNodeWithTag("poster-series:101").performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(vm.favorites.containsAll(listOf("vod:101", "series:101")))
        compose.onNodeWithTag("poster-series:101").performClick()
        compose.waitUntil(20_000) { vm.episodeSelection?.choices?.size == 2 }
        capture("screen-series.png")
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("T1 E2 · Segundo"))
        compose.onNodeWithText("T1 E2 · Segundo").performClick()
        compose.waitUntil(30_000) { streamPaths.any { it.endsWith("/202.ts") } && playerState() == "playing" }
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        showControls()
        capture("screen-player.png")
        compose.onNodeWithText("Subtitles").performClick()
        compose.onNodeWithText("Legendas").assertExists()
        compose.onNodeWithText("Desativado").performClick()
        compose.onNodeWithText("Audio Settings").performClick()
        compose.onNodeWithText("Áudio").assertExists()
        compose.onNodeWithText("Nenhuma faixa alternativa").performClick()
        compose.onNodeWithContentDescription("Voltar").performClick()

        // A series still resumes its saved episode from Details, but never enters the movie hero.
        compose.runOnIdle {
            vm.saveProgress(com.xtream.player.data.PlayerRequest("Série Teste", "unused", false,
                libraryKey = "series:101", episodeId = "202"), 6_000, 100_000)
        }
        compose.onNodeWithTag("detail-list").performScrollToIndex(0)
        val previousEpisodeRequests = streamPaths.count { it.contains("/series/") && it.endsWith("/202.ts") }
        compose.onNodeWithText("Continuar").performClick()
        compose.waitUntil(30_000) { vm.playerRequest?.episodeId == "202" && streamPaths.count { it.contains("/series/") && it.endsWith("/202.ts") } > previousEpisodeRequests }
        assertEquals(6_000L, vm.playerRequest?.resumePositionMs ?: -1L)
        showControls()
        compose.onNodeWithContentDescription("Voltar").performClick()

        // If the panel has no wide image, the second release uses its original sharp poster.
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithTag("home-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Destaque 2: Filme 102").performClick()
        compose.waitUntil(30_000) { existsUnmerged("hero-poster-loaded-vod:102") && !keyboardIsVisible() }
        capture("screen-hero-poster.png")

        compose.onNodeWithContentDescription("TV").performClick()
        compose.waitUntil(10_000) { exists("channel-1") && exists("channel-2") }
        assertSideBySide("tv-categories-pane", "tv-channel-pane", "tv-preview-pane")
        compose.onNodeWithTag("category-list").performScrollToNode(hasText("Esportes"))
        compose.onNodeWithText("Esportes").performClick()
        compose.waitUntil(10_000) { exists("channel-2") && !exists("channel-1") }
        compose.onNodeWithText("Todos").performClick()
        compose.onNodeWithTag("channel-list").performScrollToNode(hasTestTag("channel-2"))
        useRemoteInputMode()
        val liveBeforeFocus = streamPaths.count { it.startsWith("/live/") }
        compose.onNodeWithTag("channel-1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("channel-1").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("channel-2").assertIsFocused()
        compose.waitUntil(20_000) { shortRequests.get() > 0 && vm.epg["sports"].orEmpty().isNotEmpty() }
        Thread.sleep(700)
        assertEquals("D-pad focus must not start a stream", liveBeforeFocus, streamPaths.count { it.startsWith("/live/") })
        compose.onNodeWithText("Selecione um canal").assertExists()
        compose.onNodeWithTag("channel-2").performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(vm.favorites.contains("ch:2"))
        compose.onNodeWithTag("channel-2").performClick()
        compose.waitUntil(30_000) { streamPaths.any { it.endsWith("/2.ts") } && exists("preview-channel") }
        compose.onNodeWithTag("preview-channel").assertTextContains("Canal Esportes", substring = true)
        capture("screen-live.png")
        // A second explicit selection opens the selected preview in fullscreen.
        compose.onNodeWithTag("channel-2").performClick()
        compose.waitUntil(30_000) { playerState() == "playing" }
        showControls()
        val firstChannelRequests = streamPaths.count { it.endsWith("/1.ts") }
        compose.onNodeWithText("Next Channel").performClick()
        compose.waitUntil(30_000) { streamPaths.count { it.endsWith("/1.ts") } > firstChannelRequests && playerState() == "playing" }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.waitUntil(10_000) { exists("channel-2") }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("channel-1").assertIsFocused() }.isSuccess }
        compose.onNodeWithText("Selecione um canal").assertExists()
        assertFalse("Returning focus alone must not restart the preview", exists("preview-channel"))

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("Filmes e séries favoritos"))
        compose.waitUntil(10_000) { exists("poster-vod:101") && exists("poster-series:101") }
        capture("screen-favorites.png")
        val credentials = app.repository.currentCredentials()!!
        val library = UserLibrary(app).apply { select(credentials) }
        assertTrue(library.favorites().containsAll(listOf("vod:101", "series:101", "ch:2")))
        library.saveProgress("vod:101", 20_000, 100_000, null)
        val reopened = UserLibrary(app).apply { select(credentials) }
        assertEquals(20_000L, reopened.progress()["vod:101"]?.position)
        val otherAccount = UserLibrary(app).apply { select(credentials.copy(username = "other")) }
        assertTrue(otherAccount.favorites().isEmpty())
        assertTrue(otherAccount.progress().isEmpty())
        reopened.saveProgress("vod:101", 99_000, 100_000, null)
        assertTrue(reopened.progress()["vod:101"] == null)

        // Two separate Media3 players must be playing at the same time, not merely placeholders.
        compose.onNodeWithContentDescription("Multitela").performClick()
        compose.onNodeWithContentDescription("Adicionar canal 1").performClick()
        compose.onNodeWithText("Buscar canal").performTextInput("Notícias")
        compose.waitUntil(10_000) { exists("choose-channel-1") && !exists("choose-channel-2") }
        compose.onNodeWithTag("choose-channel-1").performClick()
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        compose.onNodeWithContentDescription("Adicionar canal 2").performClick()
        compose.onNodeWithTag("choose-channel-2").performClick()
        // A queued IME show from the picker must not reappear over the playback controls.
        Thread.sleep(1_000)
        compose.waitUntil(15_000) { !keyboardIsVisible() && !systemImeShown() && compose.activity.window.decorView.hasWindowFocus() }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("multi-tile-2").assertIsFocused() }.isSuccess }
        compose.waitUntil(30_000) { multiState(1) == "playing" && multiState(2) == "playing" }
        compose.onNodeWithContentDescription("Som ativo na tela 2").assertExists()
        compose.waitUntil(10_000) { multiVolumes() == listOf(0f, 1f) }
        val firstTile = compose.onNodeWithTag("multi-tile-1").fetchSemanticsNode().boundsInRoot
        val thirdTile = compose.onNodeWithTag("multi-tile-3").fetchSemanticsNode().boundsInRoot
        val soundButton = compose.onNodeWithTag("multi-sound-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Multi-screen rows must not overlap", firstTile.bottom <= thirdTile.top + 1f)
        assertTrue("Sound control must be inside its tile", soundButton.top >= firstTile.top && soundButton.bottom <= firstTile.bottom)
        // Test a real touch, not just the semantics callback; an overflowing row could
        // otherwise route this click to the empty tile below and reopen the picker.
        compose.onNodeWithTag("multi-sound-1").performTouchInput { click() }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Som ativo na tela 1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { multiVolumes() == listOf(1f, 0f) }
        assertFalse(exists("choose-channel-1"))
        useRemoteInputMode()
        compose.onNodeWithTag("multi-sound-2").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { multiVolumes() == listOf(0f, 1f) }
        compose.onNodeWithTag("multi-sound-1").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { multiVolumes() == listOf(1f, 0f) }
        compose.waitUntil(10_000) { multiState(1) == "playing" && multiState(2) == "playing" }
        compose.onNodeWithTag("multi-count-2").performClick()
        compose.waitUntil(10_000) { !exists("multi-tile-3") && !exists("multi-tile-4") }
        assertEquals(2, vm.settings.multiScreens)
        compose.onNodeWithTag("multi-count-4").performClick()
        compose.waitUntil(10_000) { exists("multi-tile-3") && exists("multi-tile-4") && multiState(1) == "playing" && multiState(2) == "playing" }
        capture("screen-multi.png")

        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Testar conexão").performScrollTo().performClick()
        compose.waitUntil(20_000) { vm.connectionTest?.contains("ms") == true }
        compose.onNodeWithText("Guia de programação (EPG)").performScrollTo().assertExists()
        val count = guideRequests.get()
        compose.onNodeWithText("Atualizar EPG").performScrollTo().performClick()
        compose.waitUntil(30_000) { guideRequests.get() > count && !vm.epgLoading && vm.epgUpdatedAt != null }
        compose.onNodeWithTag("credential-user").performScrollTo().performTextClearance()
        compose.onNodeWithTag("credential-user").performTextInput("bob")
        compose.onNodeWithText("Salvar e conectar").performScrollTo().performClick()
        compose.waitUntil(30_000) { vm.session?.username == "bob" && !vm.loading && vm.content.channels.size == 2 }
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        assertEquals("bob", app.repository.currentCredentials()?.username)
        compose.onNodeWithText("Configurações").performScrollTo()
        val settingsFieldWidth = compose.onNodeWithTag("credential-server").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Settings fields should be readable on TV", settingsFieldWidth <= 1000f * compose.activity.resources.displayMetrics.density)
        capture("screen-settings.png")
        compose.onNodeWithText("Sair da conta").performScrollTo().performClick()
        compose.waitUntil(10_000) { textExists("Adicionar conexão") }
        assertTrue(app.repository.currentCredentials() == null)
        assertEquals(0, app.epgRepository.guide.value.programCount)
        assertFalse(vm.favorites.isNotEmpty())
    }

    @Test fun landscapeTouchLayoutShowsCompactCardsAndThreeTvColumns() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)
        try {
            // The emulator's natural orientation is portrait; the activity must rotate this
            // 360x800 phone into an 800x360 landscape viewport.
            shell("wm size 360x800")
            compose.waitUntil(20_000) {
                val view = compose.activity.window.decorView
                view.width > view.height && view.width in 700..900 && view.height in 250..450
            }
            // A size change recreates the Activity; wait for the new splash to finish.
            compose.waitUntil(20_000) { textExists("Adicionar conexão") }
            compose.onNodeWithTag("app-background", useUnmergedTree = true).assertExists()
            assertLoginFitsAndIsCentered()
            compose.waitUntil(15_000) { !keyboardIsVisible() && !systemImeShown() }
            capture("screen-phone-login.png")
            assertFalse("The keyboard must not cover the initial login", keyboardIsVisible() || systemImeShown())
            compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
            compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
            compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
            compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Multitela").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { !keyboardIsVisible() }
            listOf("Home", "TV", "Filmes", "Séries", "Multitela", "Configurações").forEach {
                compose.onNodeWithContentDescription(it).assertIsDisplayed()
            }
            val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            compose.waitUntil(30_000) { vm.content.movies.size == 30 }
            compose.onNodeWithTag("home-list").performScrollToNode(hasText("Explore sua biblioteca"))
            compose.waitUntil(30_000) { exists("poster-vod:101") && existsUnmerged("loaded-poster-vod:101") }
            val homeCardWidth = compose.onAllNodesWithTag("poster-vod:101").fetchSemanticsNodes().first().boundsInRoot.width
            capture("screen-phone-home.png")

            compose.onNodeWithContentDescription("Filmes").performTouchInput { click() }
            compose.waitUntil(30_000) { exists("poster-vod:101") }
            assertSideBySide("browse-categories-pane", "catalog-grid")
            compose.waitUntil(30_000) { (101..105).all { existsUnmerged("loaded-poster-vod:$it") } }
            (101..105).forEach { compose.onNodeWithTag("poster-vod:$it").assertIsDisplayed() }
            val cardWidth = compose.onNodeWithTag("poster-vod:101").fetchSemanticsNode().boundsInRoot.width
            assertTrue("Phone posters must fit about five across", cardWidth < 120f * compose.activity.resources.displayMetrics.density)
            assertTrue("Home posters must be the same compact size", kotlin.math.abs(homeCardWidth - cardWidth) <= 2f * compose.activity.resources.displayMetrics.density)
            capture("screen-phone.png")
            compose.onNodeWithTag("catalog-grid").performTouchInput { swipeUp() }
            compose.waitUntil(10_000) {
                (106..130).any { id -> runCatching { compose.onNodeWithTag("poster-vod:$id").assertIsDisplayed() }.isSuccess } &&
                    runCatching { compose.onNodeWithTag("poster-vod:101").assertIsDisplayed() }.isFailure
            }
            capture("screen-phone-catalog-scroll.png")
            compose.onNodeWithTag("catalog-grid").performScrollToIndex(0)
            compose.waitUntil(10_000) { exists("poster-vod:101") }
            compose.onNodeWithTag("poster-vod:101").performTouchInput { click() }
            compose.waitUntil(20_000) { textExists("Reproduzir") || textExists("Continuar") }

            compose.onNodeWithContentDescription("TV").performTouchInput { click() }
            compose.waitUntil(20_000) { exists("channel-1") && exists("channel-2") }
            assertSideBySide("tv-categories-pane", "tv-channel-pane", "tv-preview-pane")
            compose.onNodeWithText("Selecione um canal").assertExists()
            assertFalse("Opening TV must not start a preview", exists("preview-channel"))
            // Use a system-level touch so Android also leaves any previous D-pad input mode.
            val channelBounds = compose.onNodeWithTag("channel-2").fetchSemanticsNode().boundsInRoot
            shell("input touchscreen tap ${channelBounds.center.x.toInt()} ${channelBounds.center.y.toInt()}")
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("preview-channel").assertTextContains("Canal Esportes", substring = true) }.isSuccess }
            capture("screen-phone-tv.png")

            compose.onNodeWithContentDescription("Multitela").performTouchInput { click() }
            compose.waitUntil(10_000) { exists("multi-tile-1") && exists("multi-tile-2") }
            assertSideBySide("multi-tile-1", "multi-tile-2")
            compose.onNodeWithContentDescription("Adicionar canal 1").assertIsDisplayed()
        } finally {
            shell("wm size 768x1376")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun tmdbDetailsHideSearchAndSupportRemoteCastAndTrailer() {
        try {
            shell("wm size 360x800")
            compose.waitUntil(20_000) {
                val view = compose.activity.window.decorView
                view.width in 700..900 && view.height in 250..450 && textExists("Adicionar conexão")
            }
            connectToPanel()
            val app = compose.activity.application as IPTVApplication
            app.tmdbRepository = TmdbRepository(ApiClient.httpClient(), server.url("/tmdb/3/"))
            val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            compose.waitUntil(30_000) { vm.content.movies.size == 30 && vm.content.series.size == 1 }

            // Without a TMDB key, provider metadata still supplies a description, cast and trailer.
            compose.onNodeWithContentDescription("Filmes").performClick()
            compose.waitUntil(15_000) { exists("poster-vod:101") && exists("search-field") }
            compose.onNodeWithTag("search-field").performTextInput("Filme Teste")
            compose.onNodeWithTag("poster-vod:101").performClick()
            compose.waitUntil(20_000) { vm.detailItem?.plot == "Sinopse real" && vm.detailExtras.cast.size == 2 && exists("detail-trailer") }
            assertFalse("Search must disappear when a movie is selected", exists("search-field"))
            assertEquals("Ator do painel", vm.detailExtras.cast.first().name)
            assertTrue("No request should be sent to TMDB without a key", tmdbRequests.isEmpty())
            // The search IME can report hidden briefly before a queued show completes.
            Thread.sleep(700)
            compose.waitUntil(15_000) { !keyboardIsVisible() && !systemImeShown() && compose.activity.window.decorView.hasWindowFocus() }
            shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(10_000) { exists("search-field") && exists("poster-vod:101") }
            compose.onNodeWithTag("search-field").assertTextContains("Filme Teste")

            // Save the user's own API key and verify it is not stored as plaintext.
            compose.onNodeWithContentDescription("Configurações").performClick()
            compose.onNodeWithTag("setting-tmdb-key").performScrollTo().performTextInput("mock-key")
            compose.onNodeWithText("Salvar chave TMDB").performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.settings.tmdbApiKey == "mock-key" }
            assertEquals("mock-key", app.sessionManager.loadSettings().tmdbApiKey)
            val storedKey = app.getSharedPreferences("iptv_native_session", Context.MODE_PRIVATE)
                .getString("setting_tmdb_api_key", "")
            assertTrue("TMDB key must not be stored in plaintext", storedKey != "mock-key" && !storedKey.isNullOrBlank())

            compose.onNodeWithContentDescription("Filmes").performClick()
            compose.waitUntil(15_000) { exists("poster-vod:101") && exists("search-field") }
            compose.onNodeWithTag("poster-vod:101").performClick()
            compose.waitUntil(25_000) { vm.detailExtras.fromTmdb && vm.detailExtras.cast.size == 8 && exists("cast-member-0") && exists("detail-trailer") }
            // The search and settings keyboards must not obscure the newly opened details.
            Thread.sleep(700)
            compose.waitUntil(15_000) { !keyboardIsVisible() && !systemImeShown() && compose.activity.window.decorView.hasWindowFocus() }
            assertEquals("Sinopse do TMDB", vm.detailItem?.plot)
            assertFalse(exists("search-field"))
            val cast = compose.onNodeWithTag("cast-member-0").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("detail-list").fetchSemanticsNode().boundsInRoot
            val visibleHeight = minOf(cast.bottom, viewport.bottom) - maxOf(cast.top, viewport.top)
            assertTrue("Part of the cast should be visible below the hero on a short screen",
                visibleHeight > 0f && visibleHeight < 140f * compose.activity.resources.displayMetrics.density)
            capture("screen-phone-detail.png")

            // Down enters the cast, then moves to its next row and scrolls it fully into view.
            useRemoteInputMode()
            compose.onNodeWithTag("detail-trailer").performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitUntil(10_000) { (0..7).any { focused("cast-member-$it") } }
            val columns = (((viewport.width / compose.activity.resources.displayMetrics.density - 42f) / 122f).toInt()).coerceIn(1, 12)
            assertTrue("Test cast must span more than one row", columns < 8)
            compose.onNodeWithTag("cast-member-0").performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitUntil(10_000) { focused("cast-member-$columns") }
            for (index in columns until 7) {
                compose.onNodeWithTag("cast-member-$index").performKeyInput { pressKey(Key.DirectionRight) }
                compose.waitUntil(10_000) { focused("cast-member-${index + 1}") }
            }
            compose.onNodeWithTag("cast-member-7").assertIsDisplayed()
            compose.onNodeWithTag("detail-list").performScrollToNode(hasTestTag("detail-synopsis"))
            compose.onNodeWithTag("detail-synopsis").assertTextContains("Sinopse do TMDB")
            compose.onNodeWithTag("detail-list").performScrollToIndex(0)

            // Verify the actual button sends only a validated HTTPS YouTube watch URL.
            Intents.init()
            try {
                val matcher = allOf(hasAction(Intent.ACTION_VIEW), hasData("https://www.youtube.com/watch?v=abcdefghijk"))
                Intents.intending(matcher).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
                compose.onNodeWithTag("detail-trailer").performClick()
                Intents.intended(matcher)
            } finally { Intents.release() }
            assertTrue(tmdbRequests.any { it.startsWith("/tmdb/3/movie/42?") })
            assertTrue(tmdbRequests.all { !it.contains("username=") && !it.contains("password=") })

            shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(10_000) { exists("search-field") }
            compose.onNodeWithContentDescription("Séries").performClick()
            compose.waitUntil(15_000) { exists("poster-series:101") }
            compose.onNodeWithTag("poster-series:101").performClick()
            compose.waitUntil(25_000) { vm.detailExtras.fromTmdb && vm.episodeSelection?.choices?.size == 2 && exists("detail-trailer") }
            assertFalse("Search must also disappear for a series", exists("search-field"))
            assertEquals("Sinopse do TMDB", vm.detailItem?.plot)
            assertTrue(tmdbRequests.any { it.startsWith("/tmdb/3/tv/77?") })
            shell("input keyevent KEYCODE_BACK")
            compose.waitUntil(10_000) { exists("search-field") && exists("poster-series:101") }

            // An invalid key must not block panel-provided episodes, cast or the details screen.
            compose.runOnIdle { vm.updateUserSettings(vm.settings.copy(tmdbApiKey = "wrong")) }
            compose.onNodeWithTag("poster-series:101").performClick()
            compose.waitUntil(20_000) { vm.detailMetadataMessage?.contains("Chave TMDB inválida") == true && vm.episodeSelection?.choices?.size == 2 }
            assertEquals("Pessoa da série", vm.detailExtras.cast.first().name)
            assertEquals("Sinopse da série", vm.detailItem?.plot)
        } finally {
            shell("wm size 768x1376")
        }
    }

    /** Runs in both the phone emulator and a real Android-TV system-image emulator in CI. */
    @OptIn(ExperimentalTestApi::class)
    @Test fun largeScreenRemoteNavigationAndOverscanAreUsable() {
        moviesCount.set(80)
        seriesCount.set(80)
        longGuide.set(true)
        val isTv = (compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        assertLoginFitsAndIsCentered()
        if (isTv) {
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithTag("login-heading").assertIsFocused() }.isSuccess &&
                    !keyboardIsVisible() && !systemImeShown()
            }
            capture("screen-android-tv-login.png")
            assertFalse("The keyboard must not cover the initial TV login", keyboardIsVisible() || systemImeShown())
            // The first remote Down goes straight from the heading to the server field.
            compose.onNodeWithTag("login-heading").performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Servidor Xtream").assertIsFocused() }.isSuccess }
            compose.waitUntil(15_000) { keyboardIsVisible() || systemImeShown() }
        }
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        if (isTv) traceIme("login typed")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("TV").fetchSemanticsNodes().isNotEmpty() }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.channels.size == 2 && vm.content.movies.size == 80 && vm.content.series.size == 80 }
        compose.waitUntil(30_000) { vm.epg["sports"].orEmpty().size == 14 }
        val view = compose.activity.window.decorView
        assertTrue("The app must be in landscape", view.width > view.height)
        if (isTv) traceIme("main opened")
        if (InstrumentationRegistry.getArguments().getString("requireTv") == "true") {
            assertTrue("This test requires an Android TV system image", isTv)
        }
        if (isTv) {
            val safe = compose.onNodeWithTag("device-safe-content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("TV controls must be inset for overscan", safe.left > 0 && safe.top > 0)
        }
        useRemoteInputMode()
        if (isTv) traceIme("remote mode")
        compose.onNodeWithContentDescription("Filmes").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(20_000) { exists("poster-vod:101") }
        if (isTv) traceIme("movies opened")
        assertSideBySide("browse-categories-pane", "catalog-grid")
        compose.onNodeWithTag("poster-vod:101").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("poster-vod:102").assertIsFocused()
        dpadDownPastFirstBatch("vod")
        if (isTv) capture("screen-android-tv-catalog.png")
        compose.onNodeWithContentDescription("Próxima página").assertDoesNotExist()
        // Switching filters starts at the first item instead of leaving a stale offset.
        compose.onNodeWithText("Ação").performClick()
        compose.waitUntil(10_000) { textExists("Nenhum item encontrado.") }
        compose.onNodeWithText("Todos").performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("poster-vod:101").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("poster-vod:101").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(20_000) { exists("detail-trailer") && exists("cast-member-0") }
        assertFalse("Movie details must hide the search bar", exists("search-field"))
        if (isTv) capture("screen-android-tv-detail.png")
        compose.onNodeWithTag("detail-trailer").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitUntil(10_000) { focused("cast-member-0") || focused("cast-member-1") }
        shell("input keyevent KEYCODE_BACK")
        compose.waitUntil(10_000) { exists("search-field") && exists("poster-vod:101") }
        compose.onNodeWithContentDescription("Séries").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(20_000) { exists("poster-series:101") }
        compose.onNodeWithTag("poster-series:101").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        dpadDownPastFirstBatch("series")
        if (isTv) traceIme("posters navigated")
        compose.onNodeWithContentDescription("TV").performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        if (isTv) traceIme("TV button focused")
        compose.onNodeWithContentDescription("TV").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(20_000) { exists("channel-1") && exists("channel-2") }
        if (isTv) traceIme("TV opened")
        assertSideBySide("tv-categories-pane", "tv-channel-pane", "tv-preview-pane")
        val liveBeforeFocus = streamPaths.count { it.startsWith("/live/") }
        compose.onNodeWithTag("channel-1").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("channel-2").assertIsFocused()
        Thread.sleep(700)
        assertEquals("Focusing TV channels must not play them", liveBeforeFocus, streamPaths.count { it.startsWith("/live/") })
        assertFalse(exists("preview-channel"))
        compose.onNodeWithContentDescription("Favoritar Canal Esportes").performClick()
        assertTrue(vm.favorites.contains("ch:2"))
        assertFalse("Favoriting must not select or play the channel", exists("preview-channel"))
        assertEquals(liveBeforeFocus, streamPaths.count { it.startsWith("/live/") })
        compose.onNodeWithTag("channel-2").performSemanticsAction(SemanticsActions.RequestFocus).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("preview-channel").assertTextContains("Canal Esportes", substring = true) }.isSuccess }
        compose.waitUntil(20_000) { previewVolume() == 1f }
        compose.onNodeWithContentDescription("Silenciar").assertExists()
        val playerIdentity = previewPlayerIdentity()
        compose.onNodeWithTag("channel-2").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithContentDescription("Silenciar").assertIsFocused() }.isSuccess }
        compose.onNodeWithContentDescription("Silenciar").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitUntil(10_000) { focused("epg-program-0") }
        repeat(10) { index ->
            compose.onNodeWithTag("epg-program-$index").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitUntil(10_000) { focused("epg-program-${index + 1}") }
        }
        compose.onNodeWithTag("epg-program-10").assertIsDisplayed()
        if (isTv) capture("screen-android-tv-guide.png")
        assertEquals("Scrolling the guide must not recreate or mute the preview", playerIdentity, previewPlayerIdentity())
        assertEquals(1f, previewVolume() ?: -1f, 0.001f)
        compose.onNodeWithTag("epg-program-10").performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitUntil(10_000) { focused("epg-program-9") }
        compose.onNodeWithContentDescription("Silenciar").performClick()
        compose.waitUntil(10_000) { previewVolume() == 0f }
        compose.onNodeWithTag("channel-1").performClick()
        compose.waitUntil(10_000) { previewVolume() == 1f && textExists("1 · Canal Notícias") }
        if (isTv) {
            // A floating Android TV IME is not always reflected in WindowInsets; inspect the
            // system IME state too, so screenshots cannot pass with a keyboard over the content.
            compose.waitUntil(15_000) { !keyboardIsVisible() && !systemImeShown() }
            capture("screen-android-tv.png")
        }
    }

    @Test fun firstLoginDisplaysRealCatalogStagesBeforeHome() {
        blockStartupStages.set(true)
        compose.waitUntil(10_000) { textExists("Adicionar conexão") }
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
        try {
            listOf(
                "get_live_categories" to "Baixando Playlist",
                "get_live_streams" to "Baixando Canais",
                "get_vod_streams" to "Baixando Filmes",
                "get_series" to "Baixando Séries",
            ).forEach { (action, label) ->
                compose.waitUntil(25_000) {
                    stageReached.getValue(action).count == 0L &&
                        runCatching { compose.onNodeWithTag("loading-message").assertTextContains(label) }.isSuccess
                }
                compose.onNodeWithTag("loading-screen").assertIsDisplayed()
                assertFalse("Home cannot open while $label is still in flight", exists("home-list"))
                stageRelease.getValue(action).countDown()
            }
            compose.waitUntil(30_000) { exists("home-list") && !exists("loading-screen") }
        } finally {
            stageRelease.values.forEach { it.countDown() }
            blockStartupStages.set(false)
        }
    }

    @Test fun profilesRestoreOfflineWithIsolatedSettingsFavoritesAndHistory() {
        connectToPanel()
        val app = compose.activity.application as IPTVApplication
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.movies.size == 30 && vm.epg["news"].orEmpty().isNotEmpty() }
        val alice = vm.profiles.single { it.username == "alice" }.id
        val originalStart = vm.epg.getValue("news").first().start
        compose.runOnIdle {
            vm.toggleFavorite("ch:1")
            vm.saveProgress(com.xtream.player.data.PlayerRequest("Filme", "unused", false, libraryKey = "vod:101"), 20_000, 100_000)
            vm.updateUserSettings(vm.settings.copy(bufferSeconds = 30, streamFormat = com.example.iptv.data.model.StreamFormat.HLS,
                autoUpdateEpg = false, epgOffsetHours = 1, userAgent = "IPTV-Test-Agent/1.0", tmdbApiKey = "alice-key"))
        }
        assertEquals(originalStart + 3_600_000L, vm.epg.getValue("news").first().start)
        assertTrue(vm.content.channels.first().url.endsWith(".m3u8"))
        compose.runOnIdle { vm.login(server.url("/").toString(), "bob", "senha") {} }
        compose.waitUntil(30_000) { !vm.loading && vm.session?.username == "bob" && vm.content.movies.size == 30 }
        assertEquals(2, vm.profiles.size)
        assertTrue("Another profile must not inherit favorites", vm.favorites.isEmpty())
        assertEquals("", vm.settings.tmdbApiKey)
        assertEquals(15, vm.settings.bufferSeconds)
        compose.runOnIdle { vm.toggleFavorite("ch:2") }
        offline.set(true)
        compose.runOnIdle { vm.switchProfile(alice) }
        compose.waitUntil(30_000) { !vm.loading && vm.session?.username == "alice" && vm.content.movies.size == 30 }
        assertTrue(vm.favorites.contains("ch:1"))
        assertFalse(vm.favorites.contains("ch:2"))
        assertEquals(30, vm.settings.bufferSeconds)
        assertEquals("IPTV-Test-Agent/1.0", vm.settings.userAgent)
        assertEquals("alice-key", vm.settings.tmdbApiKey)
        assertEquals(1, vm.settings.epgOffsetHours)
        assertTrue(vm.content.movies.first { it.id == "101" }.progress > 0f)
        val stored = app.getSharedPreferences("iptv_native_profiles", Context.MODE_PRIVATE).all.toString()
        assertFalse("Profile passwords must not be stored as plaintext", stored.contains("senha"))
        assertFalse("TMDB keys must not be stored as plaintext in profiles", stored.contains("alice-key"))
        offline.set(false)
        compose.onNodeWithContentDescription("TV").performClick()
        compose.waitUntil(10_000) { exists("channel-1") }
        compose.onNodeWithTag("channel-1").performClick()
        compose.waitUntil(30_000) { streamAgents.contains("IPTV-Test-Agent/1.0") }
        val bob = vm.profiles.single { it.username == "bob" }.id
        compose.runOnIdle { vm.removeProfile(bob) }
        compose.waitUntil(10_000) { vm.profiles.size == 1 }
        assertEquals("alice", vm.profiles.single().username)
        offline.set(false)
    }

    @Test fun epgFilterAndSubtitlePreferencesArePersistedAndApplied() {
        connectToPanel()
        val app = compose.activity.application as IPTVApplication
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.epg["news"].orEmpty().isNotEmpty() }
        compose.runOnIdle {
            vm.updateUserSettings(vm.settings.copy(onlyChannelsWithEpg = true, showFullEpg = true, subtitleSizePercent = 125))
        }
        assertTrue(app.sessionManager.loadSettings().onlyChannelsWithEpg)
        assertTrue(app.sessionManager.loadSettings().showFullEpg)
        assertEquals(125, app.sessionManager.loadSettings().subtitleSizePercent)
        compose.onNodeWithContentDescription("TV").performClick()
        compose.waitUntil(10_000) { exists("channel-1") }
        assertFalse("Channels without a loaded guide must be hidden", exists("channel-2"))
        assertTrue("Filtering must not start playback", streamPaths.none { it.startsWith("/live/") })
        compose.runOnIdle { vm.updateUserSettings(vm.settings.copy(onlyChannelsWithEpg = false)) }
        compose.waitUntil(10_000) { exists("channel-2") }
    }

    @Test fun seriesCanAdvanceToNextEpisodeWhenEnabled() {
        connectToPanel()
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.series.isNotEmpty() }
        compose.runOnIdle { vm.updateUserSettings(vm.settings.copy(autoplayNextEpisode = true)) }
        compose.onNodeWithContentDescription("Séries").performClick()
        compose.onNodeWithTag("poster-series:101").performClick()
        compose.waitUntil(20_000) { vm.episodeSelection?.choices?.size == 2 }
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("T1 E1 · Primeiro"))
        compose.onNodeWithText("T1 E1 · Primeiro").performClick()
        compose.waitUntil(30_000) { streamPaths.any { it.endsWith("/201.ts") } && playerState() == "playing" }
        compose.waitUntil(45_000) { vm.playerRequest?.episodeId == "202" && streamPaths.any { it.endsWith("/202.ts") } }
    }

    @Test fun parentalLocksFilterHomeLiveAndMultiUntilPinUnlocks() {
        connectToPanel()
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.channels.size == 2 && vm.content.movies.size == 30 }
        compose.runOnIdle { vm.setParentalPin("", "1234", "1234") {} }
        compose.waitUntil(30_000) { vm.parentalUnlocked && !vm.parentalBusy }
        compose.runOnIdle {
            vm.toggleLockedCategory("live:2")
            vm.toggleLockedCategory("vod:10")
            vm.toggleLockedCategory("series:20")
            vm.lockParental()
        }
        compose.onNodeWithContentDescription("Home").performClick()
        compose.waitUntil(10_000) { exists("hero-empty") }
        compose.onNodeWithContentDescription("TV").performClick()
        compose.waitUntil(10_000) { exists("channel-1") && !exists("channel-2") }
        compose.onNodeWithContentDescription("Multitela").performClick()
        compose.onNodeWithContentDescription("Adicionar canal 1").performClick()
        compose.waitUntil(10_000) { exists("choose-channel-1") }
        assertFalse(exists("choose-channel-2"))
        compose.onNodeWithContentDescription("Fechar").performClick()
        val result = java.util.concurrent.atomic.AtomicReference<String?>()
        compose.runOnIdle { vm.unlockParental("9999") { result.set(it) } }
        compose.waitUntil(30_000) { !vm.parentalBusy && result.get() != null }
        assertFalse(vm.parentalUnlocked)
        compose.runOnIdle { vm.unlockParental("1234") { result.set(it) } }
        compose.waitUntil(30_000) { vm.parentalUnlocked && !vm.parentalBusy }
        compose.onNodeWithContentDescription("TV").performClick()
        compose.waitUntil(10_000) { exists("channel-1") && exists("channel-2") }
    }

    private fun connectToPanel() {
        compose.waitUntil(10_000) { textExists("Adicionar conexão") }
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
        compose.waitUntil(30_000) { exists("home-list") }
    }

    @Test fun cacheRestoresCatalogHeroAndGuidesOfflineAndSurvivesPartialFailures() {
        compose.waitUntil(10_000) { textExists("Adicionar conexão") }
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
        val app = compose.activity.application as IPTVApplication
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.channels.size == 2 && vm.content.movies.size == 30 &&
            app.catalogCache.catalog.value?.isFresh() == true && app.catalogCache.heroes.value?.entries?.size == 6 &&
            app.epgRepository.guide.value.programCount == 1 }
        val credentials = app.repository.currentCredentials()!!
        compose.runOnIdle { vm.channelFocused(vm.content.channels.first { it.id == "2" }) }
        compose.waitUntil(20_000) { runBlocking { app.catalogCache.loadShortGuide(credentials).entries.containsKey("sports") } }
        compose.onNodeWithContentDescription("Destaque 1: Filme Teste").performClick()
        compose.waitUntil(30_000) { existsUnmerged("hero-backdrop-loaded-vod:101") }

        val disk = CatalogCache(app.filesDir.resolve("catalog-cache"), app.repository)
        val snapshot = runBlocking { disk.load(credentials) }!!
        assertEquals(30, snapshot.movies.size)
        assertEquals(2, snapshot.channels.size)
        assertEquals(6, runBlocking { disk.loadHeroes(credentials) }.entries.size)
        assertTrue(runBlocking { disk.loadShortGuide(credentials) }.entries.containsKey("sports"))
        assertTrue(runBlocking { disk.load(credentials.copy(username = "other")) } == null)
        val xml = XmltvRepository(app.cacheDir, ApiClient.httpClient())
        assertTrue(runBlocking { xml.loadFromDisk(credentials, "") })
        assertEquals(1, xml.guide.value.programCount)
        val cacheFile = app.filesDir.resolve("catalog-cache/${CatalogCache.accountKey(credentials)}-catalog.json.gz")
        val json = GZIPInputStream(cacheFile.inputStream()).bufferedReader().use { it.readText() }
        assertFalse("Playback credentials must not be written into catalog entries", json.contains("senha"))
        assertFalse(json.contains("/movie/alice/"))
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork(BackgroundSync.CATALOG_WORK).get(10, TimeUnit.SECONDS)
        assertTrue("A periodic three-day catalog task must be scheduled", work.any { !it.state.isFinished })
        val guideWork = WorkManager.getInstance(app).getWorkInfosForUniqueWork(BackgroundSync.GUIDE_WORK).get(10, TimeUnit.SECONDS)
        assertTrue(guideWork.any { !it.state.isFinished })

        // A fresh, newly created ViewModel must restore from disk without fetching six lists.
        val countBeforeOffline = catalogueRequests.get()
        offline.set(true)
        val store = ViewModelStore()
        lateinit var restored: AppViewModel
        compose.runOnIdle {
            app.catalogCache.forgetMemory()
            restored = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AppViewModel::class.java]
        }
        compose.waitUntil(30_000) { restored.content.movies.size == 30 && restored.content.channels.size == 2 &&
            restored.content.series.size == 1 && restored.heroDetails.containsKey("vod:101") &&
            restored.epg.containsKey("news") && restored.epg.containsKey("sports") }
        assertEquals(countBeforeOffline, catalogueRequests.get())
        val backdrop = restored.heroDetails.getValue("vod:101").backdrop!!
        val image = runBlocking { app.imageLoader.execute(ImageRequest.Builder(app).data(backdrop).size(1280, 720)
            .memoryCachePolicy(CachePolicy.DISABLED).networkCachePolicy(CachePolicy.DISABLED).build()) }
        assertTrue("Hero artwork should be available from disk even without a network", image is SuccessResult && image.dataSource == DataSource.DISK)
        compose.runOnIdle { store.clear() }
        offline.set(false)

        // Even a 200 response with an error object is not a valid list and must not erase movies.
        failVodStreams.set(true)
        val partial = runBlocking { app.catalogCache.refresh(credentials, force = true) }
        assertEquals(1, partial.failures)
        assertEquals(snapshot.movies, partial.snapshot?.movies)
        assertFalse(partial.snapshot!!.complete)
        val afterRestart = runBlocking { CatalogCache(app.filesDir.resolve("catalog-cache"), app.repository).load(credentials) }
        assertEquals(snapshot.movies, afterRestart?.movies)
        assertEquals(snapshot.movieCategories, afterRestart?.movieCategories)
    }

    @Test fun oldRefreshCannotOverwriteCacheAfterLogoutAndRelogin() = runBlocking {
        val app = compose.activity.application as IPTVApplication
        app.catalogCache.forgetMemory()
        assertTrue(app.repository.login(server.url("/").toString(), "alice", "senha") is ApiResult.Success)
        val credentials = app.repository.currentCredentials()!!
        val oldToken = app.repository.sessionToken(credentials)!!
        assertEquals(0, app.catalogCache.refresh(credentials, force = true).failures)
        blockNextVod.set(true)
        val pending = async(Dispatchers.Default) { app.catalogCache.refresh(credentials, force = true) }
        try {
            assertTrue("A catalog request should be in flight", vodBlocked.await(10, TimeUnit.SECONDS))
            app.repository.logout()
            assertTrue(app.repository.login(server.url("/").toString(), "alice", "senha") is ApiResult.Success)
            assertFalse(app.repository.isSessionCurrent(credentials, oldToken))
        } finally {
            releaseVod.countDown()
        }
        assertTrue("A response belonging to a previous session must be discarded", pending.await().snapshot == null)
        assertEquals(30, app.catalogCache.load(credentials)?.movies?.size)
        app.repository.logout()
    }

    @Test fun oldCachedAccountDatesAreCorrectedWithoutNetwork() {
        val app = compose.activity.application as IPTVApplication
        app.sessionManager.saveCredentials(PanelCredentials("http://panel.example/", "alice", "senha"))
        app.getSharedPreferences("iptv_native_session", Context.MODE_PRIVATE).edit()
            .putLong("account_expires_at", 1_900_000_000L)
            .putLong("account_created_at", 1_700_000_000L).commit()
        val cached = app.sessionManager.cachedAccount()
        assertEquals(1_900_000_000_000L, cached?.expiresAtMillis ?: 0L)
        assertEquals(1_700_000_000_000L, cached?.createdAtMillis ?: 0L)
        app.repository.logout()
    }

    /** Move through newly composed grid rows using the same Down key as a real remote. */
    @OptIn(ExperimentalTestApi::class)
    private fun dpadDownPastFirstBatch(type: String) {
        fun focusedPoster(): String? = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .singleOrNull { it.startsWith("poster-$type:") }
        repeat(80) {
            // Lazy grids can briefly have no focused semantics node while the next row is
            // composed. Wait for each real focus transition rather than racing the layout.
            compose.waitUntil(10_000) { focusedPoster() != null }
            val tag = checkNotNull(focusedPoster())
            val id = tag.substringAfter(':').toIntOrNull()
            if (id != null && id > 124) return
            shell("input keyevent KEYCODE_DPAD_DOWN")
            compose.waitUntil(10_000) { focusedPoster()?.let { it != tag } == true }
        }
        throw AssertionError("D-pad did not advance beyond the first 24 $type items")
    }

    private fun focused(tag: String): Boolean = runCatching {
        compose.onNodeWithTag(tag).assertIsFocused()
    }.isSuccess

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun previewPlayerProperty(read: (PlayerView) -> Unit) {
        compose.runOnIdle {
            fun visit(view: View) {
                if (view is PlayerView) read(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            visit(compose.activity.window.decorView)
        }
    }

    private fun previewVolume(): Float? {
        var volume: Float? = null
        previewPlayerProperty { volume = it.player?.volume }
        return volume
    }

    private fun multiVolumes(): List<Float> = mutableListOf<Float>().also { volumes ->
        previewPlayerProperty { view -> view.player?.volume?.let(volumes::add) }
    }

    private fun previewPlayerIdentity(): Int? {
        var identity: Int? = null
        previewPlayerProperty { identity = it.player?.let(System::identityHashCode) }
        return identity
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        compose.waitForIdle()
    }

    private fun useRemoteInputMode() {
        // The emulator is a touch device: send a real D-pad event to leave touch mode before
        // asserting TV focus. Clickable controls deliberately do not take focus in touch mode.
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_DPAD_DOWN")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
        compose.waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun showControls() {
        compose.onNodeWithTag("player-root").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Voltar").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun keyboardIsVisible(): Boolean =
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    private fun readShell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }
    private fun systemImeShown(): Boolean = readShell("dumpsys input_method").contains("mInputShown=true")
    private fun traceIme(label: String) {
        val flags = readShell("dumpsys input_method").lineSequence().firstOrNull { it.contains("mInputShown=") }.orEmpty().trim()
        imeTrace.add("IME $label: insets=${keyboardIsVisible()} $flags")
    }
    private fun multiState(index: Int): String? = runCatching {
        compose.onNodeWithTag("multi-player-$index", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    }.getOrNull()
    private fun playerState(): String? = runCatching {
        compose.onNodeWithTag("player-root").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    }.getOrNull()
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun existsUnmerged(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun textExists(text: String) = runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess

    private fun assertLoginFitsAndIsCentered() {
        // A window resize can update DecorView before Compose has measured the new viewport.
        // Comparing only the panel to Compose's old safe area would give a false positive.
        compose.waitUntil(20_000) {
            runCatching {
                val view = compose.activity.window.decorView
                val safe = compose.onNodeWithTag("device-safe-content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val panel = compose.onNodeWithTag("login-panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                safe.left >= 0f && safe.top >= 0f && safe.right <= view.width + 2f && safe.bottom <= view.height + 2f &&
                    kotlin.math.abs(safe.center.x - view.width / 2f) <= 3f && kotlin.math.abs(safe.center.y - view.height / 2f) <= 3f &&
                    panel.left >= safe.left && panel.top >= safe.top && panel.right <= safe.right && panel.bottom <= safe.bottom &&
                    kotlin.math.abs(panel.center.x - safe.center.x) <= 3f && kotlin.math.abs(panel.center.y - safe.center.y) <= 3f
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("login-submit").assertIsDisplayed()
    }

    private fun assertSideBySide(vararg tags: String) {
        val bounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("Expected columns $tags to be side by side", left.right <= right.left + 2f && left.top < right.bottom && right.top < left.bottom)
        }
    }

    private fun capture(name: String) {
        // Semantics may update one frame before SurfaceFlinger presents it, especially after IME dismissal.
        // Keep failure diagnostics independent of Compose idling so a stuck UI can still be captured.
        if (name.startsWith("screen-")) {
            compose.waitForIdle()
            Thread.sleep(250)
        }
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val bytes = ByteArrayOutputStream().use { output -> image.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
        image.recycle()
        writeHostFile(name, bytes)
    }
    private fun writeHostFile(name: String, bytes: ByteArray) {
        // UiAutomation uses Runtime.exec, not a shell: dd writes stdin directly without redirection.
        val descriptors = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommandRw("dd of=/data/local/tmp/iptv-$name")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { it.readBytes() }
    }

    private fun guide(): String {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val start = format.format(Date(now - 60_000)); val end = format.format(Date(now + 3_600_000))
        // Most tests exercise the short-EPG fallback for sports. Remote guide tests
        // add a longer XMLTV schedule so navigation must scroll beyond eight entries.
        val sports = if (longGuide.get()) (0 until 14).joinToString("") { index ->
            val begin = format.format(Date(now - 60_000 + index * 1_800_000L))
            val finish = format.format(Date(now - 60_000 + (index + 1) * 1_800_000L))
            """<programme channel="sports" start="$begin" stop="$finish"><title>Programa $index</title></programme>"""
        } else ""
        return """<tv><programme channel="news" start="$start" stop="$end"><title>Jornal</title></programme>$sports</tv>"""
    }
}
