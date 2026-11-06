package com.example.iptv.data.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropParsingTest {
    @Test fun catalogKeepsEachMoviesOwnBackdropAndSkipsEmptyArrayEntries() {
        val movies = XtreamParser.parseVodStreams(Json.parseToJsonElement("""[
            {"stream_id":1,"name":"One","backdrop_path":["", "null", "https://art.example/one.jpg"]},
            {"stream_id":2,"name":"Two","backdrop":"https://art.example/two.jpg"},
            {"stream_id":3,"name":"Three","backdrop_path":"","backdrop_url":"https://art.example/three.jpg"}
        ]"""))
        assertEquals(listOf("https://art.example/one.jpg", "https://art.example/two.jpg", "https://art.example/three.jpg"), movies.map { it.backdrop })
    }

    @Test fun detailsUseTheFirstNonEmptyBackdropRatherThanLosingTheWholeArray() {
        val result = XtreamParser.parseVodInfo(Json.parseToJsonElement("""{
            "info":{"backdrop_path":[null,"", "https://art.example/movie.jpg"]}
        }"""))
        assertEquals("https://art.example/movie.jpg", result.backdrop)
    }
}
