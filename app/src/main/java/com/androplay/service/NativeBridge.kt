package com.androplay.service

import android.content.Context
import android.util.Log

class NativeBridge {
    companion object {
        private const val TAG = "NativeBridge"
        val isAvailable: Boolean

        init {
            isAvailable = try {
                System.loadLibrary("androplay-native")
                Log.d(TAG, "Native library loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available", e)
                false
            }
        }
    }

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing native bridge")
    }

    fun start(deviceName: String): Boolean {
        if (!isAvailable) return false
        return try {
            nativeStart(deviceName)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native start method is unavailable", e)
            false
        }
    }

    fun stop() {
        if (!isAvailable) return
        try {
            nativeStop()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native stop method is unavailable", e)
        }
    }

    private external fun nativeStart(deviceName: String): Boolean
    private external fun nativeStop()
}
