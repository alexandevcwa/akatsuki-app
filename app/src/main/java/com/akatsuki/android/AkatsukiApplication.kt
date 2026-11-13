package com.akatsuki.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.akatsuki.android.di.AppContainer
import com.akatsuki.android.infrastructure.service.AkatsukiServerForegroundService

class AkatsukiApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AkatsukiServerForegroundService.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
