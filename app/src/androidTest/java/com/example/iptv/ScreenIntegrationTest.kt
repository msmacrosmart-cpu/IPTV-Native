package com.example.iptv

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
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
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptv.data.model.PanelCredentials
import com.xtream.player.data.AppViewModel
import com.xtream.player.data.UserLibrary
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
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
import java.util.concurrent.atomic.AtomicInteger

/** Real HTTP panel, XMLTV, H.264 transport stream and Compose/D-pad interactions. */
@RunWith(AndroidJUnit4::class)
class ScreenIntegrationTest {
    @get:Rule(order = 0)
    val cleanSession = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IPTVApplication
            app.repository.logout()
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
                    .fetchSemanticsNodes().joinToString("\n") { it.config.toString() }
                val ime = readShell("dumpsys input_method").lineSequence().filter {
                    it.contains("mInputShown") || it.contains("mShowRequested") || it.contains("mCurFocusedWindow") ||
                        it.contains("mCurClient") || it.contains("mCurMethodId") || it.contains("mServedView")
                }.joinToString("\n")
                val text = "session=${vm.session?.username} channels=${vm.content.channels.size} " +
                    "movies=${vm.content.movies.size} series=${vm.content.series.size} " +
                    "favorites=${vm.favorites} streams=$streamPaths epg=${vm.epg.keys} " +
                    "windowFocus=${compose.activity.window.decorView.hasWindowFocus()} currentFocus=${compose.activity.currentFocus?.javaClass?.simpleName} " +
                    "imeInsets=${keyboardIsVisible()}\n${imeTrace.joinToString("\n")}\n$ime\n$nodes"
                writeHostFile("ui-failure.txt", text.toByteArray())
            }
        }
    }
    private lateinit var server: MockWebServer
    private val streamPaths = CopyOnWriteArrayList<String>()
    private val imeTrace = CopyOnWriteArrayList<String>()
    private val vodInfoIds = CopyOnWriteArrayList<String>()
    private val guideRequests = AtomicInteger()
    private val shortRequests = AtomicInteger()
    private val video by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("test-pattern.ts").use { it.readBytes() }
    }

    @Before fun startPanel() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val path = url.encodedPath
                if (path.endsWith("/xmltv.php")) {
                    guideRequests.incrementAndGet()
                    return MockResponse().setHeader("Content-Type", "application/xml").setBody(guide())
                }
                if (path.startsWith("/art/")) {
                    val name = path.substringAfterLast('/')
                    val bytes = InstrumentationRegistry.getInstrumentation().targetContext.assets.open("demo/$name").use { it.readBytes() }
                    return MockResponse().setHeader("Content-Type", "image/webp").setBody(Buffer().write(bytes))
                }
                if (path.startsWith("/live/") || path.startsWith("/movie/") || path.startsWith("/series/")) {
                    streamPaths.add(path)
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
                    "get_vod_categories" -> """[{"category_id":"10","category_name":"Ação"}]"""
                    "get_vod_streams" -> {
                        val art = listOf("black-harbor", "summer-static", "house-of-tides", "field-notes", "white-room-six", "open-late", "crimson-skylark", "the-voltage-guard")
                        (101..130).joinToString(prefix = "[", postfix = "]") { id ->
                            val name = if (id == 101) "Filme Teste" else "Filme $id"
                            val poster = server.url("/art/poster-${art[(id - 101) % art.size]}.webp")
                            """{"stream_id":$id,"name":"$name","category_id":"10","container_extension":"ts","rating":"8.0","stream_icon":"$poster"}"""
                        }
                    }
                    "get_vod_info" -> {
                        val id = url.queryParameter("vod_id").orEmpty()
                        vodInfoIds.add(id)
                        // One item deliberately has no backdrop, to exercise the sharp poster fallback.
                        if (id == "102") """{"info":{"plot":"Sem arte larga"}}"""
                        else """{"info":{"plot":"Sinopse real","genre":"Ação","backdrop_path":["${server.url("/art/backdrop-black-harbor.webp")}"]}}"""
                    }
                    "get_series_categories" -> """[{"category_id":"20","category_name":"Drama"}]"""
                    "get_series" -> """[{"series_id":101,"name":"Série Teste","category_id":"20","genre":"Drama","cover":"${server.url("/art/poster-skyline-sentinels.webp")}"}]"""
                    "get_series_info" -> """{"info":{"plot":"Sinopse da série","backdrop_path":["${server.url("/art/backdrop-skyline-sentinels.webp")}"]},"episodes":{"1":[{"id":"201","episode_num":1,"season":1,"title":"Primeiro","container_extension":"ts"},{"id":"202","episode_num":2,"season":1,"title":"Segundo","container_extension":"ts"}]}}"""
                    else -> "{}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
    }

    @After fun stopPanel() { server.shutdown() }

    @OptIn(ExperimentalTestApi::class)
    @Test fun nativeScreensUseRealSessionCatalogFavoritesEpgAndPlayback() {
        compose.onNodeWithTag("app-background", useUnmergedTree = true).assertExists()
        compose.waitUntil(30_000) { existsUnmerged("app-background-loaded") }
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
        capture("screen-home.png")
        listOf("Home", "TV", "Filmes", "Séries", "Multitela", "Configurações").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
        // Assistir on the hero plays a movie directly without going through details.
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
        compose.onNodeWithContentDescription("Próxima página").performClick()
        compose.waitUntil(10_000) { exists("poster-vod:125") && !exists("poster-vod:101") }
        compose.onNodeWithContentDescription("Página anterior").performClick()
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        compose.onNodeWithTag("search-field").performTextInput("inexistente")
        compose.waitUntil(10_000) { !exists("poster-vod:101") }
        compose.onNodeWithTag("search-field").performTextClearance()
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        compose.onNodeWithTag("poster-vod:101").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithContentDescription("Séries").performClick()
        compose.waitUntil(10_000) { exists("poster-series:101") }
        compose.onNodeWithTag("poster-series:101").performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(vm.favorites.containsAll(listOf("vod:101", "series:101")))
        compose.onNodeWithTag("poster-series:101").performClick()
        compose.waitUntil(20_000) { textExists("Temporada 1") }
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

        // On the series hero, Assistir resolves episodes and resumes the saved episode.
        compose.runOnIdle {
            vm.saveProgress(com.xtream.player.data.PlayerRequest("Série Teste", "unused", false,
                libraryKey = "series:101", episodeId = "202"), 6_000, 100_000)
        }
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithTag("home-list").performScrollToIndex(0)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Destaque 2: Série Teste").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Destaque 2: Série Teste").performClick()
        compose.waitUntil(10_000) { exists("hero-watch-series:101") }
        compose.onNodeWithTag("hero-watch-series:101").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(30_000) { existsUnmerged("hero-backdrop-loaded-series:101") }
        val previousEpisodeRequests = streamPaths.count { it.contains("/series/") && it.endsWith("/202.ts") }
        compose.onNodeWithTag("hero-watch-series:101").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(30_000) { vm.playerRequest?.episodeId == "202" && streamPaths.count { it.contains("/series/") && it.endsWith("/202.ts") } > previousEpisodeRequests }
        assertEquals(6_000L, vm.playerRequest?.resumePositionMs ?: -1L)
        showControls()
        compose.onNodeWithContentDescription("Voltar").performClick()

        // If the panel has no wide image, the hero uses the original poster rather than a blur.
        compose.onNodeWithTag("search-field").performTextInput("Filme 102")
        compose.onNodeWithTag("search-field").performImeAction()
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
        compose.onNodeWithTag("channel-1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("channel-1").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("channel-2").assertIsFocused()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("preview-channel").assertTextContains("Canal Esportes", substring = true) }.isSuccess }
        compose.waitUntil(20_000) { shortRequests.get() > 0 && vm.epg["sports"].orEmpty().isNotEmpty() }
        compose.onNodeWithTag("channel-2").performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(vm.favorites.contains("ch:2"))
        capture("screen-live.png")
        compose.onNodeWithTag("channel-2").performClick()
        compose.waitUntil(30_000) { streamPaths.any { it.endsWith("/2.ts") } && playerState() == "playing" }
        showControls()
        val firstChannelRequests = streamPaths.count { it.endsWith("/1.ts") }
        compose.onNodeWithText("Next Channel").performClick()
        compose.waitUntil(30_000) { streamPaths.count { it.endsWith("/1.ts") } > firstChannelRequests && playerState() == "playing" }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.waitUntil(10_000) { exists("channel-2") }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("channel-1").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("preview-channel").assertTextContains("Canal Notícias", substring = true)

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
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("multi-tile-2").assertIsFocused() }.isSuccess }
        compose.waitUntil(30_000) { multiState(1) == "playing" && multiState(2) == "playing" }
        compose.onNodeWithContentDescription("Som ativo na tela 2").assertExists()
        compose.onNodeWithContentDescription("Ativar som da tela 1").performClick()
        compose.onNodeWithContentDescription("Som ativo na tela 1").assertExists()
        compose.waitUntil(10_000) { multiState(1) == "playing" && multiState(2) == "playing" }
        capture("screen-multi.png")

        compose.onNodeWithContentDescription("Configurações").performClick()
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
            compose.onNodeWithTag("app-background", useUnmergedTree = true).assertExists()
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
            val homeCardWidth = compose.onNodeWithTag("poster-vod:101").fetchSemanticsNode().boundsInRoot.width
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
            compose.onNodeWithTag("poster-vod:101").performTouchInput { click() }
            compose.waitUntil(20_000) { textExists("Reproduzir") || textExists("Continuar") }

            compose.onNodeWithContentDescription("TV").performTouchInput { click() }
            compose.waitUntil(20_000) { exists("channel-1") && exists("channel-2") }
            assertSideBySide("tv-categories-pane", "tv-channel-pane", "tv-preview-pane")
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("preview-channel").assertTextContains("Canal Notícias", substring = true) }.isSuccess }
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

    /** Runs in both the phone emulator and a real Android-TV system-image emulator in CI. */
    @OptIn(ExperimentalTestApi::class)
    @Test fun largeScreenRemoteNavigationAndOverscanAreUsable() {
        val isTv = (compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        if (isTv) traceIme("login typed")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("TV").fetchSemanticsNodes().isNotEmpty() }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.channels.size == 2 && vm.content.movies.size == 30 }
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
        compose.onNodeWithContentDescription("TV").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(20_000) { exists("channel-1") && exists("channel-2") }
        if (isTv) traceIme("TV opened")
        assertSideBySide("tv-categories-pane", "tv-channel-pane", "tv-preview-pane")
        compose.onNodeWithTag("channel-1").performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("channel-2").assertIsFocused()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("preview-channel").assertTextContains("Canal Esportes", substring = true) }.isSuccess }
        if (isTv) {
            // A floating Android TV IME is not always reflected in WindowInsets; inspect the
            // system IME state too, so screenshots cannot pass with a keyboard over the content.
            compose.waitUntil(15_000) { !keyboardIsVisible() && !systemImeShown() }
            capture("screen-android-tv.png")
        }
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
        // Only one channel is in XMLTV so the second must use get_short_epg.
        return """<tv><programme channel="news" start="$start" stop="$end"><title>Jornal</title></programme></tv>"""
    }
}
