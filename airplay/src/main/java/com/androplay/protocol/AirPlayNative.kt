package com.androplay.protocol

interface VideoSink {
    fun onVideoData(data: ByteArray, presentationTimeUs: Long)
    fun onSessionEnd()
}

interface AudioSink {
    /** A compressed AAC-ELD frame (screen mirroring). */
    fun onAudioData(data: ByteArray, presentationTimeUs: Long)
    /** Decoded interleaved S16 stereo PCM at 44.1 kHz (ALAC audio streaming). */
    fun onPcmData(data: ByteArray, presentationTimeUs: Long)
    /** The sender flushed (pause, seek, next track): drop audio not yet played. */
    fun onAudioFlush()
}

/**
 * AirPlay video (HLS) commands from the sender. Called on protocol threads.
 * [url] is a local http://localhost URL served by the protocol core.
 */
interface VideoPlaybackListener {
    fun onPlay(url: String, startPositionSec: Float)
    fun onSeek(positionSec: Float)
    /** 0 pauses, 1 plays. */
    fun onRate(rate: Float)
    fun onStop()
    /**
     * [durationSec, positionSec, rate, state, bufferEmpty (0/1), bufferFull (0/1)], where state is
     * [AirPlayNative.PLAYBACK_NOT_STARTED], [AirPlayNative.PLAYBACK_ACTIVE] or
     * [AirPlayNative.PLAYBACK_FINISHED]. Finished ends the sender's session, so it is only for
     * a video that played to its end or failed.
     */
    fun playbackInfo(): DoubleArray
}

object AirPlayNative {
    init {
        System.loadLibrary("androplay_protocol")
    }

    var connectionListener: (() -> Unit)? = null
    var videoPlaybackListener: VideoPlaybackListener? = null

    /**
     * Starts the protocol server and returns its port, or 0 on failure. [keyFile] stores the
     * pairing key (created on first use) so senders see the same identity after restarts.
     * [language] (BCP 47, e.g. "zh-CN") picks audio and subtitle tracks in AirPlay video.
     */
    fun start(deviceName: String, hardwareAddress: ByteArray, keyFile: String, language: String): Int {
        require(hardwareAddress.size == 6) { "AirPlay hardware address must contain six bytes" }
        return nativeStart(deviceName, hardwareAddress, keyFile, language)
    }

    fun stop() = nativeStop()
    fun isRunning(): Boolean = nativeIsRunning()
    fun setVideoSink(sink: VideoSink?) = nativeSetVideoSink(sink)
    fun setAudioSink(sink: AudioSink?) = nativeSetAudioSink(sink)

    /** `_airplay._tcp` TXT entries built by the running protocol core (empty when stopped). */
    fun airPlayTxtRecord(): Map<String, String> = parseTxt(nativeAirPlayTxtRecord())

    /** `_raop._tcp` TXT entries built by the running protocol core (empty when stopped). */
    fun raopTxtRecord(): Map<String, String> = parseTxt(nativeRaopTxtRecord())

    private fun parseTxt(entries: Array<String>): Map<String, String> =
        entries.associate { entry ->
            val separator = entry.indexOf('=')
            if (separator < 0) entry to "" else entry.substring(0, separator) to entry.substring(separator + 1)
        }

    @JvmStatic
    fun onConnectionStarted() {
        connectionListener?.invoke()
    }

    @JvmStatic
    fun onVideoPlay(url: String, startPositionSec: Float) {
        videoPlaybackListener?.onPlay(url, startPositionSec)
    }

    @JvmStatic
    fun onVideoScrub(positionSec: Float) {
        videoPlaybackListener?.onSeek(positionSec)
    }

    @JvmStatic
    fun onVideoRate(rate: Float) {
        videoPlaybackListener?.onRate(rate)
    }

    @JvmStatic
    fun onVideoStop() {
        videoPlaybackListener?.onStop()
    }

    @JvmStatic
    fun playbackInfo(): DoubleArray = videoPlaybackListener?.playbackInfo() ?: NOT_PLAYING

    const val PLAYBACK_NOT_STARTED = -1.0
    const val PLAYBACK_FINISHED = 0.0
    const val PLAYBACK_ACTIVE = 1.0

    private val NOT_PLAYING = doubleArrayOf(0.0, 0.0, 0.0, PLAYBACK_NOT_STARTED, 1.0, 0.0)

    @JvmStatic private external fun nativeStart(
        deviceName: String, hardwareAddress: ByteArray, keyFile: String, language: String
    ): Int
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeIsRunning(): Boolean
    @JvmStatic private external fun nativeAirPlayTxtRecord(): Array<String>
    @JvmStatic private external fun nativeRaopTxtRecord(): Array<String>
    @JvmStatic private external fun nativeSetVideoSink(sink: VideoSink?)
    @JvmStatic private external fun nativeSetAudioSink(sink: AudioSink?)
}

