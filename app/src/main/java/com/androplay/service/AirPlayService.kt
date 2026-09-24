package com.androplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.androplay.MainActivity

class AirPlayService : Service() {

    companion object {
        const val ACTION_START = "com.androplay.action.START_RECEIVER"
        const val ACTION_STOP = "com.androplay.action.STOP_RECEIVER"
        const val CHANNEL_ID = "androplay_channel"
        const val NOTIFICATION_ID = 1
    }

    private val manager by lazy { AirPlayManager.getInstance(this) }
    private var running = false
    private val stateCallback: (AirPlayConnectionState, StreamInfo, String?) -> Unit = { state, _, _ ->
        if (running) {
            when (state) {
                AirPlayConnectionState.AdvertisingOnly -> getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification("Discoverable only — streaming unavailable"))
                AirPlayConnectionState.Error -> {
                    running = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                else -> Unit
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        manager.registerStateCallback(stateCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (running) manager.stop()
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, buildNotification("Starting receiver"))
        val settings = ReceiverSettingsStore(this).load()
        if (!manager.start(settings)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running = true
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(
                if (manager.isDiscoveryOnly) "Publishing AirPlay discovery" else "Waiting for AirPlay connection"
            ))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (running) manager.stop()
        manager.unregisterStateCallback(stateCallback)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AndroPlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AndroPlay")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
