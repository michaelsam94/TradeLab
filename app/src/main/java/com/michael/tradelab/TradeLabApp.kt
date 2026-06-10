package com.michael.tradelab

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TradeLabApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Price & indicator alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications when a pair crosses one of your alert levels"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIGEST, "Daily indicator digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One daily summary of technical indicator readouts"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_POSITIONS, "Virtual position closes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications when a simulated position closes via take profit, stop loss, or liquidation"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Position monitor", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing status while virtual positions are open; silent, with a Stop action"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_DIGEST = "digest"
        const val CHANNEL_POSITIONS = "positions"
        const val CHANNEL_MONITOR = "monitor"
    }
}
