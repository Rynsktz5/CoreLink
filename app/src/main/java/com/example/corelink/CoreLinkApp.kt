package com.example.corelink

import android.app.Application

class CoreLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppNotifications.createChannel(this)
        CoreLinkSession.init(this)
        CoreLinkCallSession.init(this)
    }
}
