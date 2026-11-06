package com.example.iptv

import android.app.Application
import com.example.iptv.data.preferences.SessionManager

class IPTVApplication : Application() {
    lateinit var sessionManager: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionManager = SessionManager(applicationContext)
    }

    companion object {
        lateinit var instance: IPTVApplication
            private set
    }
}
