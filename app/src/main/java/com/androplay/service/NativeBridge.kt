package com.androplay.service

import android.content.Context
import android.util.Log
import com.androplay.protocol.AirPlayNative
import com.androplay.protocol.VideoSink

class NativeBridge(
    private val onConnectionStarted: () -> Unit,
    private val onVideoData: (ByteArray, Long) -> Unit,
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

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing native bridge")
        if (!isAvailable) return
        AirPlayNative.connectionListener = onConnectionStarted
        AirPlayNative.setVideoSink(object : VideoSink {
            override fun onVideoData(data: ByteArray, presentationTimeUs: Long) {
                // Qualified: an unqualified call resolves to this override and recurses.
                this@NativeBridge.onVideoData(data, presentationTimeUs)
            }

            override fun onSessionEnd() {
                this@NativeBridge.onSessionEnd.invoke()
            }
        })
    }

    fun start(deviceName: String, hardwareAddress: ByteArray): Int {
        if (!isAvailable) return 0
        return try {
            AirPlayNative.start(deviceName, hardwareAddress)
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
