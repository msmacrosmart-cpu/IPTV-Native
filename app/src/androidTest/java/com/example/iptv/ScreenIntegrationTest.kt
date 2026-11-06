package com.example.iptv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iptv.data.model.StreamFormat
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/** A local fake Xtream panel exercises the real session, navigation, EPG and settings wiring. */
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

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var server: MockWebServer
    private val customRequests = AtomicInteger(0)
    private val accountRequests = AtomicInteger(0)

    @Before fun startPanel() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.encodedPath.endsWith("/xmltv.php") || url.encodedPath.endsWith("/custom.xml")) {
                    if (url.encodedPath.endsWith("/custom.xml")) customRequests.incrementAndGet()
                    return MockResponse().setHeader("Content-Type", "application/xml").setBody(guide())
                }
                if (!url.encodedPath.endsWith("/player_api.php")) return MockResponse().setResponseCode(404)
                val body = when (url.queryParameter("action")) {
                    null -> {
                        accountRequests.incrementAndGet()
                        """{"user_info":{"username":"alice","auth":1,"status":"Active","exp_date":"1900000000","created_at":"1700000000","max_connections":"2","active_cons":"1"},"server_info":{"timezone":"America/Sao_Paulo"}}"""
                    }
                    "get_live_categories" -> """[{"category_id":"1","category_name":"Notícias"},{"category_id":"2","category_name":"Esportes"}]"""
                    "get_live_streams" -> """[{"stream_id":1,"num":1,"name":"Canal Notícias","category_id":"1","epg_channel_id":"news"},{"stream_id":2,"num":2,"name":"Canal Esportes","category_id":"2","epg_channel_id":"sports"}]"""
                    "get_short_epg" -> """{"epg_listings":[]}"""
                    "get_vod_categories", "get_vod_streams", "get_series_categories", "get_series" -> "[]"
                    else -> "{}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
    }

    @After fun stopPanel() { server.shutdown() }

    @Test fun loginFocusPreviewFullscreenEpgSettingsAndLogoutShareOneSession() {
        compose.onNodeWithText("http://servidor.com:8080").performScrollTo().performTextInput(server.url("/").toString())
        compose.onNodeWithText("Usuário").performScrollTo().performTextInput("alice")
        compose.onNodeWithText("Senha").performScrollTo().performTextInput("senha")
        compose.onNodeWithText("Entrar  →").performScrollTo().performClick()

        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("TV ao Vivo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("TV ao Vivo").performClick()
        compose.waitUntil(30_000) { runCatching { compose.onNodeWithTag("channel-2").assertExists() }.isSuccess }
        compose.onNodeWithText("Esportes").performClick()
        compose.onNodeWithTag("channel-2").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("preview-channel").assertTextEquals("Canal Esportes") }.isSuccess }
        compose.onNodeWithTag("channel-2").performClick()
        compose.onNodeWithContentDescription("Voltar").assertExists().performClick()
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Guia de programação (EPG)").assertExists()
        compose.onNodeWithText("Plano").assertExists()

        val app = compose.activity.application as IPTVApplication
        assertEquals("alice", app.repository.account.value?.username)
        assertEquals(1_900_000_000_000L, app.repository.account.value?.expiresAtMillis ?: 0L)
        compose.waitUntil(30_000) { app.epgRepository.guide.value.programCount == 2 }

        val customUrl = server.url("/custom.xml").toString()
        compose.onNodeWithText("Usar xmltv.php do servidor").performScrollTo().performTextInput(customUrl)
        compose.onNodeWithText("Atualizar EPG agora").performScrollTo().performClick()
        compose.waitUntil(30_000) { customRequests.get() > 0 && app.epgRepository.guide.value.programCount == 2 && app.repository.settings.value.lastEpgSyncMillis > 0 }
        assertEquals(customUrl, app.repository.settings.value.customXmltvUrl)
        assertTrue(app.repository.settings.value.lastEpgSyncMillis > 0)

        compose.onNodeWithText("HLS (m3u8)").performScrollTo().performClick()
        assertEquals(StreamFormat.HLS, app.repository.settings.value.streamFormat)
        assertEquals(StreamFormat.HLS, app.sessionManager.loadSettings().streamFormat)
        compose.onNodeWithText("Atualizar dados da conta").performScrollTo().performClick()
        compose.waitUntil(30_000) { accountRequests.get() >= 2 }
        compose.onNodeWithText("Sair da conta").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar saída").performScrollTo().performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Bem-vindo de volta").assertExists() }.isSuccess }
        assertTrue(app.repository.currentCredentials() == null)
        assertEquals(0, app.epgRepository.guide.value.programCount)
    }

    private fun guide(): String {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val start = format.format(Date(now - 60_000))
        val end = format.format(Date(now + 3_600_000))
        return """<tv><programme channel="news" start="$start" stop="$end"><title>Jornal</title></programme><programme channel="sports" start="$start" stop="$end"><title>Esporte ao vivo</title></programme></tv>"""
    }
}
