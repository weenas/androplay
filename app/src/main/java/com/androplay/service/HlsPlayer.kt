package com.androplay.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import com.androplay.BuildConfig
import com.androplay.protocol.AirPlayNative

/**
 * Plays AirPlay video (HLS) with ExoPlayer.
 *
 * The protocol core rewrites the sender's playlists and serves them on a local URL, so this
 * only needs to play that URL and report progress. Commands arrive on protocol threads and
 * are posted to the main thread, where ExoPlayer lives; [playbackInfo] is answered from a
 * snapshot so the sender's once-a-second poll never waits on the main thread.
 */
class HlsPlayer(
    context: Context,
    /**
     * Called on the main thread when playback ends on its own (null) or fails (a short,
     * user-facing reason).
     */
    private val onFinished: (error: String?) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** Main thread only. */
    var player: ExoPlayer? = null
        private set

    @Volatile private var snapshot = Snapshot()

    private data class Snapshot(
        val durationSec: Double = 0.0,
        val positionSec: Double = 0.0,
        val rate: Double = 0.0,
        val state: Double = AirPlayNative.PLAYBACK_NOT_STARTED,
        val buffering: Boolean = true
    )

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateSnapshot()
            if (player != null) main.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            updateSnapshot()
            if (state == Player.STATE_ENDED) {
                Log.i(TAG, "Playback ended")
                finish(null)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = updateSnapshot()

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback failed (${error.errorCodeName})", error)
            finish(describe(error))
        }
    }

    /** [onStarted] runs on the main thread once [player] exists, so the UI can attach it. */
    fun play(url: String, startPositionSec: Float, onStarted: () -> Unit) {
        // Mark active right away: the sender polls playback info while the post is pending.
        snapshot = Snapshot(positionSec = startPositionSec.toDouble(), state = AirPlayNative.PLAYBACK_ACTIVE)
        main.post { startOnMain(url, startPositionSec, onStarted) }
    }

    private fun startOnMain(url: String, startPositionSec: Float, onStarted: () -> Unit) {
        Log.i(TAG, "Playing $url from ${startPositionSec}s")
        // A stop() queued just before this play() has reset the snapshot.
        snapshot = Snapshot(positionSec = startPositionSec.toDouble(), state = AirPlayNative.PLAYBACK_ACTIVE)
        val exo = player ?: ExoPlayer.Builder(appContext).build().also {
            it.addListener(listener)
            // States, selected formats, segment loads and errors, tagged "EventLogger".
            if (BuildConfig.DEBUG) it.addAnalyticsListener(EventLogger())
            player = it
        }
        // Usually the core's local .m3u8 (HLS), but senders may also pass a plain http(s)
        // media URL, so let ExoPlayer infer the format from the URL.
        exo.setMediaItem(MediaItem.fromUri(url), (startPositionSec * 1000).toLong().coerceAtLeast(0))
        exo.prepare()
        exo.playWhenReady = true
        main.removeCallbacks(progressUpdater)
        main.post(progressUpdater)
        onStarted()
    }

    fun seek(positionSec: Float) {
        main.post { player?.seekTo((positionSec * 1000).toLong().coerceAtLeast(0)) }
    }

    fun setRate(rate: Float) {
        main.post {
            player?.playWhenReady = rate > 0f
            updateSnapshot()
        }
    }

    fun stop() {
        main.post { release() }
    }

    /** Thread-safe; see [com.androplay.protocol.VideoPlaybackListener.playbackInfo]. */
    fun playbackInfo(): DoubleArray = snapshot.let {
        doubleArrayOf(
            it.durationSec,
            it.positionSec,
            it.rate,
            it.state,
            if (it.buffering) 1.0 else 0.0,
            if (it.buffering) 0.0 else 1.0
        )
    }

    private fun finish(error: String?) {
        release()
        // Tells the sender the video is over, so it ends the session.
        snapshot = Snapshot(state = AirPlayNative.PLAYBACK_FINISHED)
        onFinished(error)
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            // The TV fetches the video itself, so a site the phone reaches only through a
            // proxy or VPN is unreachable here (e.g. googlevideo.com for YouTube).
            "Couldn't reach the video server. The TV must be able to access the video site directly."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
            "The video server refused the request."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This TV can't play the video's format."
        else -> "Video playback failed (${error.errorCodeName})."
    }

    private fun release() {
        main.removeCallbacks(progressUpdater)
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        // Keep "finished" until the next play(): the sender needs one poll to see it.
        if (snapshot.state != AirPlayNative.PLAYBACK_FINISHED) snapshot = Snapshot()
    }

    private fun updateSnapshot() {
        val exo = player ?: return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET }?.div(1000.0) ?: 0.0
        snapshot = Snapshot(
            durationSec = duration,
            positionSec = exo.currentPosition / 1000.0,
            rate = if (exo.isPlaying) 1.0 else 0.0,
            state = if (exo.playbackState == Player.STATE_ENDED) {
                AirPlayNative.PLAYBACK_FINISHED
            } else {
                AirPlayNative.PLAYBACK_ACTIVE
            },
            buffering = exo.playbackState == Player.STATE_BUFFERING
        )
    }

    private companion object {
        const val TAG = "AndroPlayHls"
        const val PROGRESS_INTERVAL_MS = 250L
    }
}
