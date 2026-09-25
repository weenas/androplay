package com.androplay.service

import android.content.Context

data class ReceiverSettings(
    val deviceName: String = "AndroPlay",
    val resolution: String = RESOLUTION_AUTO,
    val frameRate: String = FRAME_RATE_AUTO,
    /** "Auto" offers H.265 when the TV decodes it in hardware; otherwise H.264 only. */
    val videoCodec: String = CODEC_AUTO,
    /** Whether senders must enter [pin]; otherwise anyone on the network can cast. */
    val requirePassword: Boolean = false,
    /** The password (digits) used when [requirePassword] is on; kept when it is turned off. */
    val pin: String = "",
    /**
     * What happens when another device casts while one is connected: true = it takes over
     * (the current one is disconnected), false = it is refused.
     */
    val allowTakeover: Boolean = false,
    /** Start the receiver when the TV boots, so it is always ready like an Apple TV. */
    val startOnBoot: Boolean = true
) {
    /**
     * The display size advertised to senders, which they size mirroring to. "Auto" is the
     * TV's own display, capped at 1080p unless [allowUhd] (H.265 with a 4K-capable decoder):
     * H.264 mirroring gains nothing beyond 1080p.
     */
    fun displaySize(displayWidth: Int, displayHeight: Int, allowUhd: Boolean = false): Pair<Int, Int> =
        when (resolution) {
            "720p" -> 1280 to 720
            "1080p" -> 1920 to 1080
            else -> {
                val landscapeWidth = maxOf(displayWidth, displayHeight)
                val landscapeHeight = minOf(displayWidth, displayHeight)
                when {
                    landscapeWidth <= 0 || landscapeHeight <= 0 -> 1920 to 1080
                    landscapeHeight >= 2160 && allowUhd -> 3840 to 2160
                    landscapeHeight > 1080 -> 1920 to 1080
                    else -> landscapeWidth to landscapeHeight
                }
            }
        }

    /** Frames per second senders may mirror at. "Auto" is 60: TVs decode in hardware. */
    fun maxFps(): Int = if (frameRate == "30 FPS") 30 else 60

    /** The client-access password to enforce, or "" when access is open. */
    fun requiredPin(): String = pin.takeIf { requirePassword && isValidPin(it) }.orEmpty()

    companion object {
        const val RESOLUTION_AUTO = "Auto"
        const val FRAME_RATE_AUTO = "Auto"
        val RESOLUTIONS = listOf(RESOLUTION_AUTO, "720p", "1080p")
        val FRAME_RATES = listOf(FRAME_RATE_AUTO, "30 FPS", "60 FPS")
        const val CODEC_AUTO = "Auto"
        const val CODEC_H264_ONLY = "H.264 only"
        val VIDEO_CODECS = listOf(CODEC_AUTO, CODEC_H264_ONLY)

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
        videoCodec = preferences.getString("video_codec", null)
            ?.takeIf { it in ReceiverSettings.VIDEO_CODECS } ?: ReceiverSettings.CODEC_AUTO,
        pin = preferences.getString("pin", "").orEmpty(),
        // Before this setting existed, a saved PIN meant "required".
        requirePassword = preferences.getBoolean(
            "require_password",
            ReceiverSettings.isValidPin(preferences.getString("pin", "").orEmpty())
        ),
        allowTakeover = preferences.getBoolean("allow_takeover", false),
        startOnBoot = preferences.getBoolean("start_on_boot", true)
    )

    fun save(settings: ReceiverSettings) {
        preferences.edit()
            .putString("device_name", settings.deviceName.trim().ifBlank { "AndroPlay" })
            .putString("resolution", settings.resolution)
            .putString("frame_rate", settings.frameRate)
            .putString("video_codec", settings.videoCodec)
            .putString("pin", settings.pin)
            .putBoolean("require_password", settings.requirePassword)
            .putBoolean("allow_takeover", settings.allowTakeover)
            .putBoolean("start_on_boot", settings.startOnBoot)
            .remove("audio_latency")
            .apply()
    }
}
