package com.androplay.protocol

interface VideoSink {
    fun onVideoData(data: ByteArray, presentationTimeUs: Long)
    fun onSessionEnd()
}

interface AudioSink {
    fun onAudioData(data: ByteArray, presentationTimeUs: Long)
}

object AirPlayNative {
    init {
        System.loadLibrary("androplay_protocol")
    }

    var connectionListener: (() -> Unit)? = null

    fun start(deviceName: String, hardwareAddress: ByteArray): Int {
        require(hardwareAddress.size == 6) { "AirPlay hardware address must contain six bytes" }
        return nativeStart(deviceName, hardwareAddress)
    }

    fun stop() = nativeStop()
    fun isRunning(): Boolean = nativeIsRunning()
    fun setVideoSink(sink: VideoSink?) = nativeSetVideoSink(sink)
    fun setAudioSink(sink: AudioSink?) = nativeSetAudioSink(sink)

    /** `_airplay._tcp` TXT entries used by the protocol core (without `deviceid`). */
    fun airPlayTxtRecord(): Map<String, String> = parseTxt(nativeAirPlayTxtRecord())

    /** `_raop._tcp` TXT entries used by the protocol core. */
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

    @JvmStatic private external fun nativeStart(deviceName: String, hardwareAddress: ByteArray): Int
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeIsRunning(): Boolean
    @JvmStatic private external fun nativeAirPlayTxtRecord(): Array<String>
    @JvmStatic private external fun nativeRaopTxtRecord(): Array<String>
    @JvmStatic private external fun nativeSetVideoSink(sink: VideoSink?)
    @JvmStatic private external fun nativeSetAudioSink(sink: AudioSink?)
}

