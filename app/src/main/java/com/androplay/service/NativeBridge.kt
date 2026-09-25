package com.androplay.service

import android.content.Context
import com.androplay.util.Log
import com.androplay.protocol.AirPlayNative
import com.androplay.protocol.AudioSink
import com.androplay.protocol.VideoPlaybackListener
import com.androplay.protocol.VideoSink

/** Now-playing callbacks for audio streaming; called on protocol threads. */
interface AudioInfoListener {
    fun onMetadata(dmap: ByteArray)
    fun onCoverArt(image: ByteArray)
    fun onProgress(positionSec: Double, durationSec: Double)
}

class NativeBridge(
    private val onConnectionStarted: () -> Unit,
    private val onVideoData: (ByteArray, Long) -> Unit,
    private val onAudioData: (ByteArray, Long) -> Unit,
    private val onPcmData: (ByteArray, Long) -> Unit,
    private val onAudioFlush: () -> Unit,
    private val onVolume: (Float) -> Unit,
    private val audioInfo: AudioInfoListener,
    private val videoPlayback: VideoPlaybackListener,
    private val onRemoteControl: (dacpId: String, activeRemote: String) -> Unit,
    private val onSessionEnd: () -> Unit
) {
    companion object {
        private const val TAG = "NativeBridge"
        val isAvailable: Boolean

        init {
            isAvailable = try {
                Class.forName("com.androplay.protocol.AirPlayNative")
                Log.d(TAG, "AirPlay protocol library loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available", e)
                false
            }
        }
    }

    private var keyFile: String? = null
    private var language = "en"

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing native bridge")
        keyFile = java.io.File(context.noBackupFilesDir, "airplay_pairing_key.pem").absolutePath
        language = java.util.Locale.getDefault().toLanguageTag()
        if (!isAvailable) return
        AirPlayNative.connectionListener = onConnectionStarted
        AirPlayNative.videoPlaybackListener = videoPlayback
        AirPlayNative.remoteControlListener = onRemoteControl
        AirPlayNative.setVideoSink(object : VideoSink {
            override fun onVideoData(data: ByteArray, presentationTimeUs: Long) {
                // Qualified: an unqualified call resolves to this override and recurses.
                this@NativeBridge.onVideoData(data, presentationTimeUs)
            }

            override fun onSessionEnd() {
                this@NativeBridge.onSessionEnd.invoke()
            }
        })
        AirPlayNative.setAudioSink(object : AudioSink {
            override fun onAudioData(data: ByteArray, presentationTimeUs: Long) {
                this@NativeBridge.onAudioData(data, presentationTimeUs)
            }

            override fun onPcmData(data: ByteArray, presentationTimeUs: Long) {
                this@NativeBridge.onPcmData(data, presentationTimeUs)
            }

            override fun onAudioFlush() {
                this@NativeBridge.onAudioFlush.invoke()
            }

            override fun onVolume(db: Float) {
                this@NativeBridge.onVolume.invoke(db)
            }

            override fun onMetadata(dmap: ByteArray) = audioInfo.onMetadata(dmap)
            override fun onCoverArt(image: ByteArray) = audioInfo.onCoverArt(image)
            override fun onProgress(positionSec: Double, durationSec: Double) =
                audioInfo.onProgress(positionSec, durationSec)
        })
    }

    fun start(
        deviceName: String,
        hardwareAddress: ByteArray,
        displaySize: Pair<Int, Int>,
        maxFps: Int,
        password: String,
        allowTakeover: Boolean
    ): Int {
        val key = keyFile ?: return 0
        if (!isAvailable) return 0
        return try {
            AirPlayNative.start(
                deviceName, hardwareAddress, key, language,
                displaySize.first, displaySize.second, maxFps, password, allowTakeover
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native start method is unavailable", e)
            0
        }
    }

    /** TXT records the protocol core expects senders to see, or null if the library is missing. */
    fun discoveryRecords(): DiscoveryRecords? {
        if (!isAvailable) return null
        return try {
            DiscoveryRecords(
                airplay = AirPlayNative.airPlayTxtRecord(),
                raop = AirPlayNative.raopTxtRecord()
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native TXT record methods are unavailable", e)
            null
        }
    }

    fun stop() {
        if (!isAvailable) return
        try {
            AirPlayNative.stop()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native stop method is unavailable", e)
        }
    }

}
