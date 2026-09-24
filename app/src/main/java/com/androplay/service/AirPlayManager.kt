package com.androplay.service

import android.content.Context
import android.util.Log
import android.view.Surface

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
    private val videoRenderer = VideoRenderer()
    private val settingsStore = ReceiverSettingsStore(context)

    private val _stateCallbacks = mutableListOf<(AirPlayConnectionState, StreamInfo, String?) -> Unit>()

    private var currentState: AirPlayConnectionState = AirPlayConnectionState.Idle
        set(value) {
            field = value
            notifyStateChange(value)
        }

    @Volatile private var currentStreamInfo: StreamInfo = StreamInfo()
    private var currentError: String? = null

    init {
        nativeBridge.initialize(context)
    }

    fun start(settings: ReceiverSettings = settingsStore.load()): Boolean {
        Log.d(TAG, "Starting AirPlay server: ${settings.deviceName}")
        currentError = null
        if (!nativeBridge.start(settings.deviceName)) {
            currentError = "AirPlay receiver engine is not included in this build"
            currentState = AirPlayConnectionState.Error
            return false
        }
        currentState = AirPlayConnectionState.Discovering
        return true
    }

    fun stop() {
        Log.d(TAG, "Stopping AirPlay server")
        nativeBridge.stop()
        videoRenderer.stop()
        currentStreamInfo = StreamInfo()
        currentError = null
        currentState = AirPlayConnectionState.Idle
    }

    fun registerStateCallback(callback: (AirPlayConnectionState, StreamInfo, String?) -> Unit) {
        _stateCallbacks.add(callback)
        callback(currentState, currentStreamInfo, currentError)
    }

    fun unregisterStateCallback(callback: (AirPlayConnectionState, StreamInfo, String?) -> Unit) {
        _stateCallbacks.remove(callback)
    }

    private fun notifyStateChange(state: AirPlayConnectionState) {
        _stateCallbacks.forEach { it(state, currentStreamInfo, currentError) }
    }

    fun onNativeStreamStarted(name: String, model: String, width: Int, height: Int, fps: Int,
                              sampleRate: Int, channels: Int, isMirroring: Boolean) {
        if (isMirroring && width > 0 && height > 0) {
            videoRenderer.configure(width, height)
        }
        currentStreamInfo = StreamInfo(name, model, width, height, fps, sampleRate, channels, isMirroring, true)
        currentError = null
        currentState = AirPlayConnectionState.Streaming
    }

    fun onNativeStreamStopped() {
        videoRenderer.stop()
        currentStreamInfo = StreamInfo()
        currentState = AirPlayConnectionState.Discovering
    }

    fun onNativeError(error: String) {
        videoRenderer.stop()
        currentError = error
        currentState = AirPlayConnectionState.Error
    }

    fun setVideoSurface(surface: Surface?) {
        videoRenderer.setSurface(surface)
    }

    /** UxPlay's video callback will forward complete Annex B frames here once its core is linked. */
    fun onNativeVideoData(data: ByteArray, presentationTimeUs: Long, isH265: Boolean) {
        val stream = currentStreamInfo
        if (!stream.isMirroring || stream.videoWidth <= 0 || stream.videoHeight <= 0) return
        videoRenderer.configure(stream.videoWidth, stream.videoHeight, isH265)
        videoRenderer.render(data, presentationTimeUs)
    }
}
