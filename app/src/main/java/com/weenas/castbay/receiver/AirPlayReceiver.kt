package com.weenas.castbay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weenas.castbay.service.AirPlayManager

class AirPlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.weenas.castbay.action.AIRPLAY_STATE_CHANGED" -> {
                val state = intent.getStringExtra("state") ?: return
                // Forward state changes to UI
            }
        }
    }
}
