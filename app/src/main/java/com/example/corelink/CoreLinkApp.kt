package com.example.corelink

import android.app.Application
import com.google.android.material.color.DynamicColors

class CoreLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        AppNotifications.createChannel(this)
        CoreLinkSession.init(this)
        CoreLinkCallSession.init(this)
    }
}
