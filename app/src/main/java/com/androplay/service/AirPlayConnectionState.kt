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
    val frameHeight: Int = 0,
    /** AirPlay video (the sender streams a URL, e.g. from the YouTube app) rather than mirroring. */
    val isVideoPlayback: Boolean = false,
    /** Audio streaming without video (e.g. a music app); [nowPlaying] describes it. */
    val isAudioOnly: Boolean = false,
    val nowPlaying: NowPlaying = NowPlaying()
)
