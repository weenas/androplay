package com.androplay.service

import android.content.Context
import android.util.Log

class NativeBridge {
    companion object {
        private const val TAG = "NativeBridge"

        init {
            try {
                System.loadLibrary("androplay-native")
                Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available, using Java fallback", e)
            }
        }
    }

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing native bridge")
    }

    fun start(deviceName: String) {
        Log.d(TAG, "Starting native AirPlay server: $deviceName")
    }

    fun stop() {
        Log.d(TAG, "Stopping native AirPlay server")
    }
}
