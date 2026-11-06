package com.example.iptv.data.epg

import com.example.iptv.data.model.LiveChannel
import com.example.iptv.data.model.PanelCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class XmltvRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var credentials: PanelCredentials
    private val http = OkHttpClient()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        credentials = PanelCredentials(server.url("/panel/").toString(), "user+one", "p@ss &word")
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun defaultSourcePreservesPanelSubpathAndEncodesCredentials() {
        val url = XmltvRepository.sourceUrl(credentials, "").toHttpUrl()
        assertEquals("/panel/xmltv.php", url.encodedPath)
        assertEquals(credentials.username, url.queryParameter("username"))
        assertEquals(credentials.password, url.queryParameter("password"))
        assertEquals("https://example.com/guide.xml?token=abc", XmltvRepository.sourceUrl(credentials, " https://example.com/guide.xml?token=abc "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpCustomSource() {
        XmltvRepository.sourceUrl(credentials, "file:///data/private.xml")
    }

    @Test fun refreshCachesGuideAndFailedDownloadDoesNotReplaceIt() = runBlocking {
        val repository = XmltvRepository(folder.root, http)
        server.enqueue(MockResponse().setBody(guideXml()))
        assertTrue(repository.refresh(credentials, ""))
        assertEquals(1, repository.guide.value.channelCount)
        assertEquals(1, repository.guide.value.programCount)
        assertEquals("Programa", repository.guide.value.forChannel(LiveChannel(1, "Canal", epgChannelId = "CANAL.BR")).single().title)
        val request = server.takeRequest()
        assertEquals("/panel/xmltv.php", request.requestUrl?.encodedPath)
        assertEquals(credentials.password, request.requestUrl?.queryParameter("password"))

        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(repository.refresh(credentials, ""))
        assertTrue(repository.state.value is EpgUpdateState.Error)
        assertEquals(1, repository.guide.value.programCount)

        val afterRestart = XmltvRepository(folder.root, http)
        assertTrue(afterRestart.loadFromDisk(credentials, ""))
        assertEquals(1, afterRestart.guide.value.programCount)
    }

    @Test fun badXmlNeverOverwritesLastValidFileAndOtherAccountsCannotSeeIt() = runBlocking {
        val repository = XmltvRepository(folder.root, http)
        server.enqueue(MockResponse().setBody(guideXml()))
        assertTrue(repository.refresh(credentials, ""))
        server.enqueue(MockResponse().setBody("not an XMLTV guide"))
        assertFalse(repository.refresh(credentials, ""))
        assertEquals(1, repository.guide.value.programCount)
        val restored = XmltvRepository(folder.root, http)
        assertTrue(restored.loadFromDisk(credentials, ""))
        assertEquals(1, restored.guide.value.programCount)
        assertFalse(restored.loadFromDisk(credentials.copy(username = "other-user"), ""))
        assertEquals(0, restored.guide.value.programCount)
        restored.clear()
        assertFalse(restored.loadFromDisk(credentials, ""))
    }

    @Test fun invalidatingSourcePreventsAnInFlightDownloadFromRestoringOldState() = runBlocking {
        val repository = XmltvRepository(folder.root, http)
        val slowBody = guideXml().replace("</tv>", " ".repeat(100_000) + "</tv>")
        server.enqueue(MockResponse().setBody(slowBody).throttleBody(1024, 30, TimeUnit.MILLISECONDS))
        val job = launch(Dispatchers.Default) { repository.refresh(credentials, "") }
        withTimeout(10_000) {
            while (repository.state.value !is EpgUpdateState.Downloading) delay(10)
        }
        repository.invalidate()
        job.cancelAndJoin()
        assertEquals(EpgUpdateState.Idle, repository.state.value)
        assertEquals(0, repository.guide.value.programCount)
        assertFalse(repository.loadFromDisk(credentials, ""))
    }

    private fun guideXml(): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        val from = formatter.format(Date(now - 60_000))
        val to = formatter.format(Date(now + 60_000))
        return "<tv><programme channel=\"canal.br\" start=\"$from\" stop=\"$to\"><title>Programa</title></programme></tv>"
    }
}
