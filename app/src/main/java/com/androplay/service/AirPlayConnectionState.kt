package com.androplay.service

enum class AirPlayConnectionState {
    Idle,
    Discovering,
    Registering,
    AdvertisingOnly,
    Connecting,
    Connected,
    Streaming,
    Disconnected,
    Error
}

data class StreamInfo(
    val sourceName: String = "",
    val sourceModel: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoFps: Int = 0,
    val audioSampleRate: Int = 44100,
    val audioChannels: Int = 2,
    val isMirroring: Boolean = false,
    val isPlaying: Boolean = false,
    /** Visible size of the decoded picture (crop rect); 0 until the decoder reports it. */
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
)
