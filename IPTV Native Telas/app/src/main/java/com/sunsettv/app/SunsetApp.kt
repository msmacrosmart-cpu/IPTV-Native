package com.sunsettv.app

import android.app.Application
import com.sunsettv.app.core.ServiceLocator

class SunsetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
