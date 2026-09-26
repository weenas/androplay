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
        private const val PAUSE_CHECK_MS = 500L
        /** playback-info for an AirPlay sender whose video DLNA replaced: finished, so it ends its session. */
        private val AIRPLAY_VIDEO_REPLACED =
            doubleArrayOf(0.0, 0.0, 0.0, com.androplay.protocol.AirPlayNative.PLAYBACK_FINISHED, 0.0, 1.0)

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
        onVideoData = { data, pts, isH265 -> onNativeVideoData(data, pts, isH265) },
        onAudioData = { data, _ -> audioRenderer.render(data) },
        onPcmData = { data, _, compressedBytes -> onPcmAudio(data, compressedBytes) },
        onAudioFlush = {
            audioRenderer.flush()
            updateNowPlaying { it.paused() }
        },
        onVolume = { db ->
            volumeDb = db
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
            // Once DLNA has taken the player over, the AirPlay sender's commands no longer apply
            // and it is told its video is over.
            override fun onSeek(positionSec: Float) {
                if (videoSource != VideoSource.DLNA) hlsPlayer.seek(positionSec)
            }
            override fun onRate(rate: Float) {
                if (videoSource != VideoSource.DLNA) hlsPlayer.setRate(rate)
            }
            override fun onStop() {
                if (videoSource != VideoSource.DLNA) onVideoStopped(null)
            }
            override fun playbackInfo(): DoubleArray =
                if (videoSource == VideoSource.DLNA) AIRPLAY_VIDEO_REPLACED else hlsPlayer.playbackInfo()
        },
        onSessionEnd = ::onNativeStreamStopped
    )
    private val discoveryAdvertiser = AirPlayDiscoveryAdvertiser(context)
    private val videoRenderer = VideoRenderer(context, onFrameSizeChanged = ::onFrameSizeChanged)
    private val audioRenderer = AudioRenderer()
    private val displayManager = context.getSystemService(android.hardware.display.DisplayManager::class.java)
    private val hevcSupport by lazy { HevcSupport.detect() }

    /** The mirroring profile offered by the running receiver; decoders are sized to it. */
    @Volatile private var advertised = MirroringProfile(h265 = false, width = DEFAULT_VIDEO_WIDTH, height = DEFAULT_VIDEO_HEIGHT)

    /** The codec and display size [settings] offer senders on this TV. */
    fun mirroringProfile(settings: ReceiverSettings): MirroringProfile =
        panelSize().let { (width, height) -> MirroringProfile.of(settings, hevcSupport, width, height) }

    /**
     * The panel's largest mode. Many 4K TVs render their UI in a 1080p mode and switch up only
     * for video, so the current mode would under-report the panel.
     */
    private fun panelSize(): Pair<Int, Int> {
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val largest = display.supportedModes.maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight }
            ?: display.mode
        return largest.physicalWidth to largest.physicalHeight
    }
    private val hlsPlayer = HlsPlayer(context, onFinished = ::onVideoStopped)
    @Volatile private var nowPlaying = NowPlaying()
    /** The sender's last volume (AirPlay dB), for the stats overlay. */
    @Volatile private var volumeDb: Float? = null
    private val dacp = DacpClient(context)
    private val mediaSession = NowPlayingSession(context, onCommand = ::remoteControl)
    private val dlna = com.androplay.dlna.DlnaReceiver(context)
    /** The running receiver's settings (DLNA checks the second-device policy against them). */
    @Volatile private var activeSettings = ReceiverSettings()

    /** Who started the video in [hlsPlayer]: AirPlay (e.g. YouTube) or DLNA (e.g. Bilibili's cast button). */
    private enum class VideoSource { AIRPLAY, DLNA }
    @Volatile private var videoSource: VideoSource? = null

    /** DLNA senders' commands, played with the same player, screen and quick menu as AirPlay video. */
    private val dlnaTarget = object : com.androplay.dlna.DlnaRenderer.Target {
        @Volatile private var url: String? = null
        @Volatile private var title: String? = null
        @Volatile private var volume = 100
        @Volatile private var muted = false

        override fun open(url: String, title: String?) {
            if (!activeSettings.allowTakeover && airPlayBusy()) {
                Log.i(TAG, "DLNA video refused: another device is casting over AirPlay")
                throw com.androplay.dlna.Soap.Fault(701, "Another device is casting to this TV")
            }
            this.url = url
            this.title = title
            start(url, title)
        }

        private fun start(url: String, title: String?) {
            Log.i(TAG, "DLNA video: ${title ?: "(no title)"} · $url")
            // Like AirPlay video, it replaces whatever is on screen. Playback starts right away:
            // some senders never send Play after SetAVTransportURI.
            videoSource = VideoSource.DLNA
            videoRenderer.stop()
            audioRenderer.stop()
            applyVolume()
            hlsPlayer.play(url, 0f) {
                currentStreamInfo = StreamInfo(sourceName = title.orEmpty(), isVideoPlayback = true)
                currentError = null
                currentState = AirPlayConnectionState.Streaming
            }
        }

        private fun ours() = videoSource == VideoSource.DLNA

        /** AirPlay is on screen: mirroring, music, or AirPlay video. */
        private fun airPlayBusy(): Boolean {
            val stream = currentStreamInfo
            return currentState == AirPlayConnectionState.Streaming &&
                (stream.isMirroring || stream.isAudioOnly || (stream.isVideoPlayback && videoSource == VideoSource.AIRPLAY))
        }

        override fun play() {
            val progress = hlsPlayer.progress()
            if (ours() && progress.active) {
                hlsPlayer.setRate(1f)
            } else {
                // After Stop or the end, or once AirPlay took over: Play starts the video again.
                url?.let { start(it, title) }
            }
        }

        override fun pause() {
            if (ours()) hlsPlayer.setRate(0f)
        }

        override fun stop() {
            if (ours()) onVideoStopped(null)
        }

        override fun seek(positionSec: Double) {
            if (ours()) hlsPlayer.seek(positionSec.toFloat())
        }

        override fun setVolume(percent: Int) {
            volume = percent
            applyVolume()
        }

        override fun setMuted(muted: Boolean) {
            this.muted = muted
            applyVolume()
        }

        private fun applyVolume() {
            if (ours()) hlsPlayer.setVolume(if (muted) 0f else volume / 100f)
        }

        override fun status(): com.androplay.dlna.DlnaRenderer.Status {
            val progress = hlsPlayer.progress()
            val state = when {
                !ours() || !progress.active -> com.androplay.dlna.DlnaState.STOPPED
                progress.buffering -> com.androplay.dlna.DlnaState.TRANSITIONING
                progress.playing -> com.androplay.dlna.DlnaState.PLAYING
                else -> com.androplay.dlna.DlnaState.PAUSED
            }
            return com.androplay.dlna.DlnaRenderer.Status(
                state = state,
                positionSec = if (ours()) progress.positionSec else 0.0,
                durationSec = if (ours()) progress.durationSec else 0.0,
                volume = volume,
                muted = muted
            )
        }
    }

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
        activeSettings = settings
        currentError = null
        val protocolPort = nativeBridge.start(
            settings.deviceName,
            discoveryAdvertiser.hardwareAddress(),
            mirroringProfile(settings).also { advertised = it },
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
        // DLNA (video apps' own cast buttons, e.g. Bilibili's) runs beside AirPlay.
        if (settings.dlnaEnabled) Thread({ dlna.start(settings.deviceName, dlnaTarget) }, "DLNA-start").start()
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
        dlna.stop()
        videoRenderer.stop()
        audioRenderer.stop()
        hlsPlayer.stop()
        videoSource = null
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
        stopDlnaVideo()
        currentStreamInfo = StreamInfo(name, model, width, height, fps, sampleRate, channels, isMirroring, true)
        currentError = null
        currentState = AirPlayConnectionState.Streaming
    }

    fun onNativeStreamStopped() {
        videoRenderer.stop()
        audioRenderer.stop()
        nowPlaying = NowPlaying()
        dacp.clear()
        mediaSession.update(null)
        // An AirPlay session ending (e.g. a phone that was only probing) leaves DLNA video playing.
        if (videoSource == VideoSource.DLNA && currentStreamInfo.isVideoPlayback) return
        hlsPlayer.stop()
        currentStreamInfo = StreamInfo()
        currentState = AirPlayConnectionState.Discovering
    }

    private fun onNativeConnectionStarted() {
        currentError = null
        // DLNA video stays on screen until the AirPlay sender actually streams something.
        if (videoSource == VideoSource.DLNA && currentStreamInfo.isVideoPlayback) return
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
        // AirPlay video replaces any mirroring session (or DLNA video) on this receiver.
        videoSource = VideoSource.AIRPLAY
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
    private fun onPcmAudio(pcm: ByteArray, compressedBytes: Int) {
        audioRenderer.renderPcm(pcm, compressedBytes)
        lastAudioAtMs = android.os.SystemClock.elapsedRealtime()
        if (!nowPlaying.playing) updateNowPlaying { it.resumed() }
        if (currentState == AirPlayConnectionState.Connecting || videoSource == VideoSource.DLNA) {
            stopDlnaVideo()
            currentStreamInfo = StreamInfo(isAudioOnly = true, nowPlaying = nowPlaying)
            currentState = AirPlayConnectionState.Streaming
            mediaSession.update(nowPlaying)
            mainHandler.removeCallbacks(pauseWatchdog)
            mainHandler.postDelayed(pauseWatchdog, PAUSE_CHECK_MS)
        }
    }

    @Volatile private var lastAudioAtMs = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Freezes the now-playing progress when audio stops arriving (the sender paused). */
    private val pauseWatchdog = object : Runnable {
        override fun run() {
            if (!currentStreamInfo.isAudioOnly) return
            val lastAudio = lastAudioAtMs
            if (nowPlaying.stalled(lastAudio)) updateNowPlaying { it.paused(nowMs = lastAudio) }
            mainHandler.postDelayed(this, PAUSE_CHECK_MS)
        }
    }

    /** A "stats for nerds" snapshot of the current stream. Main thread; null when idle. */
    fun playbackStats(): PlaybackStats? {
        val stream = currentStreamInfo
        val stats = when {
            stream.isVideoPlayback ->
                hlsPlayer.stats(if (videoSource == VideoSource.DLNA) "DLNA video" else "AirPlay video") ?: return null
            stream.isMirroring -> PlaybackStats("Screen mirroring", videoRenderer.stats(), audioRenderer.stats())
            stream.isAudioOnly -> PlaybackStats("AirPlay audio", audio = audioRenderer.stats())
            else -> return null
        }
        val volume = volumeDb?.let { db ->
            if (db <= AirPlayVolume.MIN_DB) "muted" else "%.1f dB".format(java.util.Locale.US, db)
        }
        return if (volume == null) stats else stats.copy(extra = stats.extra + ("Volume" to volume))
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

    /**
     * Ends whatever is being cast, from the TV (the remote's Back key or the quick menu):
     * DLNA video stops (its sender sees STOPPED), an AirPlay sender is disconnected.
     */
    fun endCasting() {
        Log.i(TAG, "Casting ended from the TV")
        if (videoSource == VideoSource.DLNA && currentStreamInfo.isVideoPlayback) {
            onVideoStopped(null)
            return
        }
        nativeBridge.disconnect()
        // Leave the screen now rather than when the connections have closed.
        videoRenderer.stop()
        audioRenderer.stop()
        hlsPlayer.stop()
        videoSource = null
        nowPlaying = NowPlaying()
        dacp.clear()
        mediaSession.update(null)
        currentStreamInfo = StreamInfo()
        currentState = AirPlayConnectionState.Discovering
    }

    /** AirPlay mirroring or audio starting takes the screen from DLNA video. */
    private fun stopDlnaVideo() {
        if (videoSource != VideoSource.DLNA) return
        videoSource = null
        hlsPlayer.stop()
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
            // The decoder is sized to the display offered to senders (up to 4K with H.265).
            stream = StreamInfo(
                videoWidth = advertised.width,
                videoHeight = advertised.height,
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
