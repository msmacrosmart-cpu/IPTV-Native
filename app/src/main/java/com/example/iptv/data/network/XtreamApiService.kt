package com.example.iptv.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Xtream Codes `player_api.php` endpoints.
 *
 * Responses are returned as raw [ResponseBody] on purpose: real-world panels are wildly
 * inconsistent (numbers vs. strings, missing fields, `{}` instead of `[]`), so the JSON is
 * parsed defensively by [XtreamParser] instead of a strict serializer that would throw.
 */
interface XtreamApiService {

    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<ResponseBody>

    @GET("player_api.php")
    suspend fun categories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): Response<ResponseBody>

    @GET("player_api.php")
    suspend fun streams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("category_id") categoryId: String?
    ): Response<ResponseBody>

    @GET("player_api.php")
    suspend fun shortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("stream_id") streamId: Int,
        @Query("limit") limit: Int
    ): Response<ResponseBody>

    @GET("player_api.php")
    suspend fun vodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("vod_id") vodId: Int
    ): Response<ResponseBody>

    @GET("player_api.php")
    suspend fun seriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("series_id") seriesId: Int
    ): Response<ResponseBody>
}

object XtreamActions {
    const val LIVE_CATEGORIES = "get_live_categories"
    const val LIVE_STREAMS = "get_live_streams"
    const val SHORT_EPG = "get_short_epg"
    const val VOD_CATEGORIES = "get_vod_categories"
    const val VOD_STREAMS = "get_vod_streams"
    const val VOD_INFO = "get_vod_info"
    const val SERIES_CATEGORIES = "get_series_categories"
    const val SERIES = "get_series"
    const val SERIES_INFO = "get_series_info"
}
