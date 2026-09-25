package com.androplay.service

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.media3.exoplayer.ExoPlayer
import com.androplay.protocol.VideoPlaybackListener

class AirPlayManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AirPlayManager"
        private const val DEFAULT_VIDEO_WIDTH = 1920
        private const val DEFAULT_VIDEO_HEIGHT = 1080

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

    private val nativeBridge = NativeBridge(
        onConnectionStarted = ::onNativeConnectionStarted,
        onVideoData = { data, pts -> onNativeVideoData(data, pts, false) },
        onAudioData = { data, _ -> audioRenderer.render(data) },
        videoPlayback = object : VideoPlaybackListener {
            override fun onPlay(url: String, startPositionSec: Float) = onVideoPlay(url, startPositionSec)
            override fun onSeek(positionSec: Float) = hlsPlayer.seek(positionSec)
            override fun onRate(rate: Float) = hlsPlayer.setRate(rate)
            override fun onStop() = onVideoStopped(null)
            override fun playbackInfo(): DoubleArray = hlsPlayer.playbackInfo()
        },
        onSessionEnd = ::onNativeStreamStopped
    )
    private val discoveryAdvertiser = AirPlayDiscoveryAdvertiser(context)
    private val videoRenderer = VideoRenderer(onFrameSizeChanged = ::onFrameSizeChanged)
    private val audioRenderer = AudioRenderer()
    private val hlsPlayer = HlsPlayer(context, onFinished = ::onVideoStopped)

    /** The AirPlay video player while one is active. Main thread only. */
    val videoPlayer: ExoPlayer? get() = hlsPlayer.player
    private val settingsStore = ReceiverSettingsStore(context)

    private val _stateCallbacks = mutableListOf<(AirPlayConnectionState, StreamInfo, String?) -> Unit>()

    private var currentState: AirPlayConnectionState = AirPlayConnectionState.Idle
        set(value) {
            field = value
            notifyStateChange(value)
        }

    @Volatile private var currentStreamInfo: StreamInfo = StreamInfo()
    private var currentError: String? = null

    val isDiscoveryOnly: Boolean
        get() = currentState == AirPlayConnectionState.Registering ||
            currentState == AirPlayConnectionState.AdvertisingOnly

    init {
        nativeBridge.initialize(context)
    }

    fun start(settings: ReceiverSettings = settingsStore.load()): Boolean {
        Log.d(TAG, "Starting AirPlay server: ${settings.deviceName}")
        currentError = null
        val protocolPort = nativeBridge.start(settings.deviceName, discoveryAdvertiser.hardwareAddress())
        if (!discoveryAdvertiser.start(
                settings.deviceName,
                protocolPort.takeIf { it > 0 },
                records = nativeBridge.discoveryRecords() ?: DiscoveryRecords.FALLBACK,
                onReady = {
                    Log.i(TAG, "AirPlay discovery records are visible on the local network")
                    if (currentState == AirPlayConnectionState.Registering) {
                        currentState = if (protocolPort > 0) {
                            AirPlayConnectionState.Discovering
                        } else {
                            AirPlayConnectionState.AdvertisingOnly
                        }
                    }
                },
                onError = ::onNativeError
            )) {
            currentError = "Could not start local network discovery"
            currentState = AirPlayConnectionState.Error
            return false
        }
        currentState = AirPlayConnectionState.Registering
        return true
    }

    fun stop() {
        Log.d(TAG, "Stopping AirPlay server")
        nativeBridge.stop()
        discoveryAdvertiser.stop()
        videoRenderer.stop()
        audioRenderer.stop()
        hlsPlayer.stop()
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
        audioRenderer.stop()
        hlsPlayer.stop()
        currentStreamInfo = StreamInfo()
        currentState = AirPlayConnectionState.Discovering
    }

    private fun onNativeConnectionStarted() {
        currentError = null
        currentState = AirPlayConnectionState.Connecting
    }

    fun onNativeError(error: String) {
        discoveryAdvertiser.stop()
        videoRenderer.stop()
        audioRenderer.stop()
        hlsPlayer.stop()
        currentError = error
        currentState = AirPlayConnectionState.Error
    }

    fun setVideoSurface(surface: Surface?) {
        videoRenderer.setSurface(surface)
    }

    private fun onVideoPlay(url: String, startPositionSec: Float) {
        // AirPlay video replaces any mirroring session on this receiver.
        videoRenderer.stop()
        audioRenderer.stop()
        hlsPlayer.play(url, startPositionSec) {
            currentStreamInfo = StreamInfo(isVideoPlayback = true)
            currentError = null
            currentState = AirPlayConnectionState.Streaming
        }
    }

    /** [error] is shown on the waiting screen until the next connection. */
    private fun onVideoStopped(error: String?) {
        hlsPlayer.stop()
        if (currentStreamInfo.isVideoPlayback) {
            currentStreamInfo = StreamInfo()
            currentError = error
            currentState = AirPlayConnectionState.Discovering
        }
    }

    private fun onFrameSizeChanged(width: Int, height: Int) {
        currentStreamInfo = currentStreamInfo.copy(frameWidth = width, frameHeight = height)
        notifyStateChange(currentState)
    }

    /** The protocol core forwards complete Annex B frames here for MediaCodec decoding. */
    fun onNativeVideoData(data: ByteArray, presentationTimeUs: Long, isH265: Boolean) {
        var stream = currentStreamInfo
        if (!stream.isMirroring) {
            stream = StreamInfo(
                videoWidth = DEFAULT_VIDEO_WIDTH,
                videoHeight = DEFAULT_VIDEO_HEIGHT,
                videoFps = 60,
                audioSampleRate = 0,
                audioChannels = 0,
                isMirroring = true
            )
            currentStreamInfo = stream
            currentState = AirPlayConnectionState.Streaming
        }
        videoRenderer.configure(stream.videoWidth, stream.videoHeight, isH265)
        videoRenderer.render(data, presentationTimeUs)
    }

}
