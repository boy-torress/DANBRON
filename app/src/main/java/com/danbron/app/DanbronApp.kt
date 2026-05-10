package com.danbron.app

import android.app.Application
import com.danbron.app.notifications.BronNotificationWorker
import com.danbron.app.notifications.NotificationHelper

class DanbronApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create notification channel
        NotificationHelper.createChannel(this)
        // Schedule Bron's proactive notifications every 4 hours
        BronNotificationWorker.schedule(this)
    }
}
