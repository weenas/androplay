package com.androplay.service

import android.content.Context
import android.util.Log

class AirPlayManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AirPlayManager"

        @Volatile
        private var instance: AirPlayManager? = null

        fun getInstance(context: Context): AirPlayManager {
            return instance ?: synchronized(this) {
                instance ?: AirPlayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val nativeBridge = NativeBridge()

    private val _stateCallbacks = mutableListOf<(AirPlayConnectionState, StreamInfo, String?) -> Unit>()

    private var currentState: AirPlayConnectionState = AirPlayConnectionState.Idle
        set(value) {
            field = value
            notifyStateChange(value)
        }

    private var currentStreamInfo: StreamInfo = StreamInfo()
    private var currentError: String? = null

    init {
        nativeBridge.initialize(context)
    }

    fun start() {
        Log.d(TAG, "Starting AirPlay server")
        currentState = AirPlayConnectionState.Discovering
        // Native implementation would handle discovery
        nativeBridge.start("AndroPlay")
    }

    fun stop() {
        Log.d(TAG, "Stopping AirPlay server")
        nativeBridge.stop()
        currentState = AirPlayConnectionState.Idle
        currentStreamInfo = StreamInfo()
    }

    fun registerStateCallback(callback: (AirPlayConnectionState, StreamInfo, String?) -> Unit) {
        _stateCallbacks.add(callback)
        callback(currentState, currentStreamInfo, currentError)
    }

    fun unregisterStateCallback() {
        _stateCallbacks.clear()
    }

    private fun notifyStateChange(state: AirPlayConnectionState) {
        _stateCallbacks.forEach { it(state, currentStreamInfo, currentError) }
    }

    private fun notifyStreamStarted(info: StreamInfo) {
        currentStreamInfo = info
        currentError = null
        _stateCallbacks.forEach { it(currentState, info, null) }
    }

    fun onNativeStreamStarted(name: String, model: String, width: Int, height: Int, fps: Int,
                              sampleRate: Int, channels: Int, isMirroring: Boolean) {
        currentState = AirPlayConnectionState.Streaming
        notifyStreamStarted(
            StreamInfo(name, model, width, height, fps, sampleRate, channels, isMirroring, true)
        )
    }

    fun onNativeStreamStopped() {
        currentState = AirPlayConnectionState.Idle
        currentStreamInfo = StreamInfo()
        notifyStateChange(ConnectionState.Idle)
    }

    fun onNativeError(error: String) {
        currentError = error
        _stateCallbacks.forEach { it(currentState, currentStreamInfo, error) }
    }
}
