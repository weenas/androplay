package com.androplay.service

import android.content.Context

data class ReceiverSettings(
    val deviceName: String = "AndroPlay",
    val resolution: String = RESOLUTION_AUTO,
    val frameRate: String = FRAME_RATE_AUTO,
    /** Password senders must enter to connect; blank = anyone on the network can. */
    val pin: String = ""
) {
    /**
     * The display size advertised to senders, which they size mirroring to. "Auto" is the
     * TV's own display, capped at 1080p: H.264 mirroring gains nothing beyond that.
     */
    fun displaySize(displayWidth: Int, displayHeight: Int): Pair<Int, Int> = when (resolution) {
        "720p" -> 1280 to 720
        "1080p" -> 1920 to 1080
        else -> {
            val landscapeWidth = maxOf(displayWidth, displayHeight)
            val landscapeHeight = minOf(displayWidth, displayHeight)
            if (landscapeWidth <= 0 || landscapeHeight <= 0 || landscapeHeight > 1080) {
                1920 to 1080
            } else {
                landscapeWidth to landscapeHeight
            }
        }
    }

    /** Frames per second senders may mirror at. "Auto" is 60: TVs decode in hardware. */
    fun maxFps(): Int = if (frameRate == "30 FPS") 30 else 60

    /** The client-access password to enforce, or "" when access is open. */
    fun accessPassword(): String = pin.takeIf { isValidPin(it) }.orEmpty()

    companion object {
        const val RESOLUTION_AUTO = "Auto"
        const val FRAME_RATE_AUTO = "Auto"
        val RESOLUTIONS = listOf(RESOLUTION_AUTO, "720p", "1080p")
        val FRAME_RATES = listOf(FRAME_RATE_AUTO, "30 FPS", "60 FPS")

        /** UxPlay requires client-access passwords of at least 4 characters. */
        const val MIN_PIN_LENGTH = 4

        fun isValidPin(pin: String) = pin.length >= MIN_PIN_LENGTH && pin.all { it.isDigit() }
    }
}

class ReceiverSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("receiver_settings", Context.MODE_PRIVATE)

    fun load() = ReceiverSettings(
        deviceName = preferences.getString("device_name", "AndroPlay").orEmpty().ifBlank { "AndroPlay" },
        // Values from older versions (e.g. "4K") fall back to Auto.
        resolution = preferences.getString("resolution", null)
            ?.takeIf { it in ReceiverSettings.RESOLUTIONS } ?: ReceiverSettings.RESOLUTION_AUTO,
        frameRate = preferences.getString("frame_rate", null)
            ?.takeIf { it in ReceiverSettings.FRAME_RATES } ?: ReceiverSettings.FRAME_RATE_AUTO,
        pin = preferences.getString("pin", "").orEmpty()
    )

    fun save(settings: ReceiverSettings) {
        preferences.edit()
            .putString("device_name", settings.deviceName.trim().ifBlank { "AndroPlay" })
            .putString("resolution", settings.resolution)
            .putString("frame_rate", settings.frameRate)
            .putString("pin", settings.pin)
            .remove("audio_latency")
            .apply()
    }
}
