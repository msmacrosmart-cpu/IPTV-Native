package com.example.iptv.data.network

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * OkHttp/Retrofit factory for the Xtream Codes API.
 *
 * One shared [OkHttpClient] (connection pool reused) and a lightweight Retrofit instance per
 * panel base URL. No converter factory is needed because the API returns raw JSON bodies that
 * are parsed by [XtreamParser].
 */
object ApiClient {

    /** Some panels reject requests without a known user agent. */
    const val USER_AGENT = "IPTVNative/2.0 (Android; +https://github.com)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        allowTrailingComma = true
        explicitNulls = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun httpClient(): OkHttpClient = httpClient

    fun create(baseUrl: String): XtreamApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(httpClient)
            .build()
            .create(XtreamApiService::class.java)
    }
}
