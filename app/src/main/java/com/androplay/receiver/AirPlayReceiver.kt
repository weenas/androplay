package com.androplay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androplay.service.AirPlayManager

class AirPlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.androplay.action.AIRPLAY_STATE_CHANGED" -> {
                val state = intent.getStringExtra("state") ?: return
                // Forward state changes to UI
            }
        }
    }
}
