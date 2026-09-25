package com.androplay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.androplay.service.AirPlayService
import com.androplay.service.ReceiverSettingsStore

/** Starts the receiver when the TV boots, if "Start when the TV turns on" is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        if (!ReceiverSettingsStore(context).load().startOnBoot) return
        Log.i(TAG, "Starting AirPlay receiver after boot (${intent.action})")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AirPlayService::class.java).setAction(AirPlayService.ACTION_START)
            )
        } catch (error: IllegalStateException) {
            // Android 15+ no longer lets BOOT_COMPLETED start mediaPlayback foreground services
            // (ForegroundServiceStartNotAllowedException); the user starts it from the app.
            Log.w(TAG, "Not allowed to start the receiver at boot", error)
        }
    }

    private companion object {
        const val TAG = "AndroPlayBoot"
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Fast-boot variants some TV and phone vendors send instead.
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
