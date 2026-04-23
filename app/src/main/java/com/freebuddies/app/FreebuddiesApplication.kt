package com.freebuddies.app

import android.app.Application

class FreebuddiesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FreeBudsConnectionManager.init(this)
        FreeBudsConnectionManager.startAutoReconnect(this)
    }
}
