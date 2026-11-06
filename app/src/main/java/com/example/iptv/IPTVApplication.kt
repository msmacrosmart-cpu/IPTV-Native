package com.example.iptv

import android.app.Application
import com.example.iptv.data.preferences.SessionManager
import com.example.iptv.data.repository.XtreamRepository

/**
 * Application entry point. Owns the session storage and the single [XtreamRepository]
 * instance used by every ViewModel, so the HTTP client and the credentials survive
 * activity recreation and configuration changes.
 *
 * The class name must match `android:name` in AndroidManifest.xml (".IPTVApplication").
 */
class IPTVApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var repository: XtreamRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        repository = XtreamRepository(sessionManager)
    }
}
