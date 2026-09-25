package com.androplay.service

import android.content.Context
import com.androplay.util.Log
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
        onPcmData = { data, _ -> onPcmAudio(data) },
        onAudioFlush = {
            audioRenderer.flush()
            updateNowPlaying { it.paused() }
        },
        onVolume = { db ->
            val gain = AirPlayVolume.toGain(db)
            audioRenderer.setVolume(gain)
            hlsPlayer.setVolume(gain)
        },
        onRemoteControl = { dacpId, activeRemote -> dacp.setSender(dacpId, activeRemote) },
        audioInfo = object : AudioInfoListener {
            override fun onMetadata(dmap: ByteArray) {
                val track = DmapMetadata.parse(dmap) ?: return
                updateNowPlaying { it.copy(title = track.title, artist = track.artist, album = track.album) }
            }

            override fun onCoverArt(image: ByteArray) =
                updateNowPlaying { it.copy(coverArt = image.takeIf { bytes -> bytes.isNotEmpty() }) }

            override fun onProgress(positionSec: Double, durationSec: Double) = updateNowPlaying {
                it.copy(
                    positionSec = positionSec,
                    durationSec = durationSec,
                    positionAtMs = android.os.SystemClock.elapsedRealtime()
                )
            }
        },
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
    private val displayManager = context.getSystemService(android.hardware.display.DisplayManager::class.java)
    private val displayMode
        get() = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY).mode
    private val hlsPlayer = HlsPlayer(context, onFinished = ::onVideoStopped)
    @Volatile private var nowPlaying = NowPlaying()
    private val dacp = DacpClient(context)
    private val mediaSession = NowPlayingSession(context, onCommand = ::remoteControl)

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
        Log.init(context)
        nativeBridge.initialize(context)
    }

    fun start(settings: ReceiverSettings = settingsStore.load()): Boolean {
        Log.d(TAG, "Starting AirPlay server: ${settings.deviceName}")
        currentError = null
        val protocolPort = nativeBridge.start(
            settings.deviceName,
            discoveryAdvertiser.hardwareAddress(),
            settings.displaySize(displayMode.physicalWidth, displayMode.physicalHeight),
            settings.maxFps(),
            settings.requiredPin(),
            settings.allowTakeover
        )
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

    /** Applies changed settings to a running receiver; senders reconnect to the new one. */
    fun restartIfRunning(settings: ReceiverSettings) {
        if (currentState == AirPlayConnectionState.Idle || currentState == AirPlayConnectionState.Error) return
        Log.d(TAG, "Restarting AirPlay server to apply settings")
        stop()
        start(settings)
    }

    fun stop() {
        Log.d(TAG, "Stopping AirPlay server")
        nativeBridge.stop()
        discoveryAdvertiser.stop()
        videoRenderer.stop()
        audioRenderer.stop()
        hlsPlayer.stop()
        nowPlaying = NowPlaying()
        dacp.clear()
        mediaSession.update(null)
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
        nowPlaying = NowPlaying()
        dacp.clear()
        mediaSession.update(null)
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

    /**
     * Audio streaming (music apps) has no picture, so the first PCM frame switches the screen
     * from "Connecting" to what is playing.
     */
    private fun onPcmAudio(pcm: ByteArray) {
        audioRenderer.renderPcm(pcm)
        if (!nowPlaying.playing) updateNowPlaying { it.resumed() }
        if (currentState == AirPlayConnectionState.Connecting) {
            currentStreamInfo = StreamInfo(isAudioOnly = true, nowPlaying = nowPlaying)
            currentState = AirPlayConnectionState.Streaming
            mediaSession.update(nowPlaying)
        }
    }

    /** TV-remote control of AirPlay video (e.g. YouTube): pause/resume. */
    fun toggleVideoPause() = hlsPlayer.togglePause()

    /** TV-remote control of AirPlay video: skip by [deltaSec] (negative rewinds). */
    fun seekVideoBy(deltaSec: Int) = hlsPlayer.seekBy(deltaSec)

    /** Controls the sender's playback (music apps), from the TV remote or the screen. */
    fun remoteControl(command: DacpClient.Command) {
        Log.d(TAG, "Remote control: ${command.path}")
        dacp.send(command)
    }

    /** Metadata can arrive before the audio does, so it is kept until the screen shows it. */
    @Synchronized
    private fun updateNowPlaying(transform: (NowPlaying) -> NowPlaying) {
        nowPlaying = transform(nowPlaying)
        if (currentStreamInfo.isAudioOnly) {
            currentStreamInfo = currentStreamInfo.copy(nowPlaying = nowPlaying)
            notifyStateChange(currentState)
            mediaSession.update(nowPlaying)
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
