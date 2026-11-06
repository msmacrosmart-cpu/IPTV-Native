package com.example.iptv

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
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
                val text = "session=${vm.session?.username} channels=${vm.content.channels.size} " +
                    "movies=${vm.content.movies.size} series=${vm.content.series.size} " +
                    "favorites=${vm.favorites} streams=$streamPaths epg=${vm.epg.keys}\n$nodes"
                writeHostFile("ui-failure.txt", text.toByteArray())
            }
        }
    }
    private lateinit var server: MockWebServer
    private val streamPaths = CopyOnWriteArrayList<String>()
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
                    "get_vod_streams" -> """[{"stream_id":101,"name":"Filme Teste","category_id":"10","container_extension":"ts","rating":"8.0"}]"""
                    "get_vod_info" -> """{"info":{"plot":"Sinopse real","genre":"Ação"}}"""
                    "get_series_categories" -> """[{"category_id":"20","category_name":"Drama"}]"""
                    "get_series" -> """[{"series_id":101,"name":"Série Teste","category_id":"20","genre":"Drama"}]"""
                    "get_series_info" -> """{"episodes":{"1":[{"id":"201","episode_num":1,"season":1,"title":"Primeiro","container_extension":"ts"},{"id":"202","episode_num":2,"season":1,"title":"Segundo","container_extension":"ts"}]}}"""
                    else -> "{}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
    }

    @After fun stopPanel() { server.shutdown() }

    @Test fun suppliedScreensUseRealSessionCatalogFavoritesEpgAndPlayback() {
        capture("screen-login.png")
        compose.onNodeWithText("Servidor Xtream").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        compose.onNodeWithContentDescription("Entrar").performScrollTo().performClick()

        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("TV ao vivo").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { !keyboardIsVisible() }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(30_000) { vm.content.channels.size == 2 && vm.content.movies.size == 1 && vm.content.series.size == 1 }
        val app = compose.activity.application as IPTVApplication
        assertEquals(1_900_000_000_000L, app.repository.account.value?.expiresAtMillis ?: 0L)
        compose.waitUntil(30_000) { app.epgRepository.guide.value.programCount == 1 }
        capture("screen-home.png")

        // The same numeric ID for a movie and a series must not collide.
        compose.onNodeWithContentDescription("Filmes").performClick()
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        capture("screen-movies.png")
        compose.onNodeWithTag("search-field").performTextInput("inexistente")
        compose.waitUntil(10_000) { !exists("poster-vod:101") }
        compose.onNodeWithTag("search-field").performTextClearance()
        compose.waitUntil(10_000) { exists("poster-vod:101") }
        compose.onNodeWithTag("poster-vod:101").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithContentDescription("Séries").performClick()
        compose.waitUntil(10_000) { exists("poster-series:101") }
        capture("screen-series.png")
        compose.onNodeWithTag("poster-series:101").performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(vm.favorites.containsAll(listOf("vod:101", "series:101")))
        compose.onNodeWithTag("poster-series:101").performClick()
        compose.waitUntil(20_000) { textExists("T1 E2 · Segundo") }
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

        compose.onNodeWithContentDescription("TV ao vivo").performClick()
        compose.waitUntil(10_000) { exists("channel-1") && exists("channel-2") }
        compose.onNodeWithTag("category-list").performScrollToNode(hasText("Esportes"))
        compose.onNodeWithText("Esportes").performClick()
        compose.waitUntil(10_000) { exists("channel-2") && !exists("channel-1") }
        compose.onNodeWithText("Todos").performClick()
        compose.onNodeWithTag("channel-list").performScrollToNode(hasTestTag("channel-2"))
        useRemoteInputMode()
        compose.onNodeWithTag("channel-2").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("channel-2").assertIsFocused()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("preview-channel").assertTextContains("Canal Esportes", substring = true) }.isSuccess
        }
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

        compose.onNodeWithContentDescription("Favoritos").performClick()
        compose.onNodeWithText("FAVORITOS").assertExists()
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

        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Guia de programação (EPG)").assertExists()
        compose.onNodeWithText("Conta").assertExists()
        val count = guideRequests.get()
        compose.onNodeWithText("Atualizar EPG").performScrollTo().performClick()
        compose.waitUntil(30_000) { guideRequests.get() > count && !vm.epgLoading && vm.epgUpdatedAt != null }
        capture("screen-settings.png")
        compose.onNodeWithText("Sair da conta").performScrollTo().performClick()
        compose.waitUntil(10_000) { textExists("Bem-vindo de volta") }
        assertTrue(app.repository.currentCredentials() == null)
        assertEquals(0, app.epgRepository.guide.value.programCount)
        assertFalse(vm.favorites.isNotEmpty())
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
    private fun playerState(): String? = runCatching {
        compose.onNodeWithTag("player-root").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    }.getOrNull()
    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess
    private fun textExists(text: String) = runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess

    private fun capture(name: String) {
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
