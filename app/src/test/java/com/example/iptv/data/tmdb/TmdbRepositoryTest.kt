package com.example.iptv.data.tmdb

import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.network.XtreamParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TmdbRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: TmdbRepository

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        repo = TmdbRepository(OkHttpClient.Builder().readTimeout(3, TimeUnit.SECONDS).build(), server.url("/3/"))
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun usesProviderTmdbIdAndParsesRealCastTrailerAndImages() = runBlocking {
        server.enqueue(MockResponse().setBody("""{
            "overview":"Sinopse em português","release_date":"2024-01-01","vote_average":8.5,
            "backdrop_path":"/wide.jpg","genres":[{"name":"Ação"}],
            "credits":{"cast":[{"name":"Atriz Teste","character":"Heroína","profile_path":"/person.jpg"}]},
            "videos":{"results":[{"site":"YouTube","type":"Trailer","official":true,"key":"abcdefghijk"}]}
        }"""))
        val result = repo.lookup("O Filme", "2024", false, "42", "fake-key") as TmdbResult.Found
        assertEquals("Sinopse em português", result.metadata.details.plot)
        assertEquals("https://image.tmdb.org/t/p/w1280/wide.jpg", result.metadata.details.backdrop)
        assertEquals("Ação", result.metadata.details.genre)
        assertEquals("abcdefghijk", result.metadata.extras.youtubeId)
        assertEquals(CastMember("Atriz Teste", "Heroína", "https://image.tmdb.org/t/p/w185/person.jpg"), result.metadata.extras.cast.single())
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/3/movie/42", request.requestUrl!!.encodedPath)
        assertEquals("fake-key", request.requestUrl!!.queryParameter("api_key"))
        assertEquals("pt-BR", request.requestUrl!!.queryParameter("language"))
        assertNull(request.requestUrl!!.queryParameter("username"))
        assertNull(request.requestUrl!!.queryParameter("password"))
        assertEquals(1, server.requestCount)
    }

    @Test fun searchesSeriesAndRejectsDifferentTitles() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[{"id":1,"name":"Outro título"},{"id":9,"name":"Série Teste"}]}"""))
        server.enqueue(MockResponse().setBody("""{"overview":"Descrição","first_air_date":"2021-01-01","credits":{"cast":[]},"videos":{"results":[]}}"""))
        val result = repo.lookup("[PT] Série Teste (2021) FHD", "2021", true, null, "key") as TmdbResult.Found
        assertEquals("Descrição", result.metadata.details.plot)
        assertNull(result.metadata.extras.youtubeId)
        val search = server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("/3/search/tv", search.encodedPath)
        assertEquals("Série Teste", search.queryParameter("query"))
        assertEquals("2021", search.queryParameter("first_air_date_year"))
        assertEquals("/3/tv/9", server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)

        server.enqueue(MockResponse().setBody("""{"results":[{"id":10,"title":"Filme diferente"}]}"""))
        assertEquals(TmdbResult.NotFound, repo.lookup("Filme Teste", "", false, null, "key"))
    }

    @Test fun providerMetadataIsUsefulWithoutAKeyAndOnlyOfficialYoutubeUrlsAreAccepted() {
        val details = XtreamParser.parseVodInfo(ApiClient.json.parseToJsonElement("""{"info":{
            "cast":"Pessoa Um, Pessoa Dois","tmdb_id":"42","youtube_trailer":"https://youtu.be/abcdefghijk"}}"""))
        assertEquals("42", details.tmdbId)
        assertEquals(listOf("Pessoa Um", "Pessoa Dois"), providerExtras(details).cast.map { it.name })
        assertEquals("abcdefghijk", providerExtras(details).youtubeId)
        assertEquals("abcdefghijk", youtubeVideoId("https://www.youtube.com/watch?v=abcdefghijk"))
        assertNull(youtubeVideoId("https://youtube.com.attacker.example/watch?v=abcdefghijk"))
        assertNull(youtubeVideoId("https://attacker.example/abcdefghijk"))
        assertNull(youtubeVideoId("too-short"))
    }

    @Test fun retainsTheEntireCastRatherThanTruncatingIt() = runBlocking {
        val cast = (1..35).joinToString(",") { """{"name":"Pessoa $it"}""" }
        server.enqueue(MockResponse().setBody("""{"credits":{"cast":[$cast]}}"""))
        val result = repo.lookup("Teste", "", false, "1", "key") as TmdbResult.Found
        assertEquals(35, result.metadata.extras.cast.size)
        assertEquals("Pessoa 35", result.metadata.extras.cast.last().name)
    }

    @Test fun neverFollowsRedirectsWithTheApiKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/unexpected")))
        assertEquals(TmdbResult.Unavailable, repo.lookup("Teste", "", false, "1", "secret"))
        assertEquals(1, server.requestCount)
    }

    @Test fun unauthorizedAndOfflineFailGracefullyAndRequestsAreCancellable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(TmdbResult.Unauthorized, repo.lookup("Teste", "", false, "1", "wrong"))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertNull(withTimeoutOrNull(250) { repo.lookup("Teste", "", false, "1", "key") })
    }
}
