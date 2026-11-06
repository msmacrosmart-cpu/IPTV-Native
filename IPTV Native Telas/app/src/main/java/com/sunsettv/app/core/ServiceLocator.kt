package com.sunsettv.app.core

import android.content.Context
import com.sunsettv.app.data.EpgRepository
import com.sunsettv.app.data.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** DI manual e simples. Inicializado em [com.sunsettv.app.SunsetApp]. */
object ServiceLocator {
    private lateinit var app: Context

    fun init(context: Context) { app = context.applicationContext }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    val client by lazy { XtreamClient(http) }
    val prefs by lazy { AppPrefs(app) }
    val session by lazy { SessionStore(app) }
    val epg by lazy { EpgRepository(client, prefs, app.cacheDir, appScope) }
}
