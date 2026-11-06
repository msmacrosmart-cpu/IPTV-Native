package com.example.iptv.data.network
import com.example.iptv.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query
interface XtreamApi {
 @GET("player_api.php") suspend fun authenticate(@Query("username") user:String,@Query("password") pass:String): AuthResponse
 @GET("player_api.php") suspend fun live(@Query("username") user:String,@Query("password") pass:String,@Query("action") action:String="get_live_streams"): List<StreamItem>
 @GET("player_api.php") suspend fun vod(@Query("username") user:String,@Query("password") pass:String,@Query("action") action:String="get_vod_streams"): List<VodItem>
 @GET("player_api.php") suspend fun series(@Query("username") user:String,@Query("password") pass:String,@Query("action") action:String="get_series"): List<VodItem>
}
