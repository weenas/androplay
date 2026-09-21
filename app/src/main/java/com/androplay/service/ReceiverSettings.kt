package com.androplay.service

import android.content.Context

data class ReceiverSettings(
    val deviceName: String = "AndroPlay",
    val resolution: String = "Auto",
    val frameRate: String = "Auto",
    val audioLatency: String = "250 ms",
    val pin: String = ""
)

class ReceiverSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("receiver_settings", Context.MODE_PRIVATE)

    fun load() = ReceiverSettings(
        deviceName = preferences.getString("device_name", "AndroPlay").orEmpty().ifBlank { "AndroPlay" },
        resolution = preferences.getString("resolution", "Auto").orEmpty(),
        frameRate = preferences.getString("frame_rate", "Auto").orEmpty(),
        audioLatency = preferences.getString("audio_latency", "250 ms").orEmpty(),
        pin = preferences.getString("pin", "").orEmpty()
    )

    fun save(settings: ReceiverSettings) {
        preferences.edit()
            .putString("device_name", settings.deviceName.trim().ifBlank { "AndroPlay" })
            .putString("resolution", settings.resolution)
            .putString("frame_rate", settings.frameRate)
            .putString("audio_latency", settings.audioLatency)
            .putString("pin", settings.pin)
            .apply()
    }
}
