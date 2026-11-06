package com.example.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.iptv.data.cache.CatalogCache
import com.example.iptv.data.cache.HeroLoader
import com.example.iptv.data.epg.XmltvRepository
import com.example.iptv.data.network.ApiClient
import com.example.iptv.data.preferences.SessionManager
import com.example.iptv.data.repository.XtreamRepository

/**
 * Application entry point. Owns the session storage and the single [XtreamRepository]
 * instance used by every ViewModel, so the HTTP client and the credentials survive
 * activity recreation and configuration changes.
 *
 * The class name must match `android:name` in AndroidManifest.xml (".IPTVApplication").
 */
class IPTVApplication : Application(), ImageLoaderFactory {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var epgRepository: XmltvRepository
        private set

    lateinit var repository: XtreamRepository
        private set

    lateinit var catalogCache: CatalogCache
        private set

    lateinit var heroLoader: HeroLoader
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        epgRepository = XmltvRepository(cacheDir, ApiClient.httpClient())
        repository = XtreamRepository(sessionManager)
        catalogCache = CatalogCache(filesDir.resolve("catalog-cache"), repository)
        heroLoader = HeroLoader(this, repository, catalogCache)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil-images"))
            .maxSizeBytes(256L * 1024 * 1024).build() }
        // Some panels send no-cache headers even for unchanging artwork.
        .respectCacheHeaders(false)
        .build()
}
