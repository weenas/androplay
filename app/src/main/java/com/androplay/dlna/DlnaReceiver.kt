package com.androplay.dlna

import android.content.Context
import com.androplay.util.Log
import java.util.UUID

/**
 * AndroPlay as a DLNA media renderer, next to AirPlay: video apps' own "cast" buttons (Bilibili,
 * iQiyi, Youku, ...) find it and hand it a media URL to play.
 */
class DlnaReceiver(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("dlna_identity", Context.MODE_PRIVATE)
    private var http: DlnaHttpServer? = null
    private var ssdp: SsdpServer? = null

    /** Stable across restarts, so control points keep recognising the TV. */
    private val uuid: String by lazy {
        preferences.getString(KEY_UUID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_UUID, it).apply()
        }
    }

    @Synchronized
    fun start(name: String, target: DlnaRenderer.Target) {
        stop()
        val friendlyName = name.trim().ifBlank { "AndroPlay" }
        val server = DlnaHttpServer(DlnaRenderer(target)) { UpnpDescriptions.device(friendlyName, uuid) }
        try {
            val port = server.start()
            http = server
            ssdp = SsdpServer(uuid) { server.port }.also { it.start() }
            Log.i(TAG, "DLNA renderer \"$friendlyName\" on port $port (uuid $uuid)")
        } catch (error: Exception) {
            Log.e(TAG, "Could not start the DLNA renderer", error)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        ssdp?.stop()
        http?.stop()
        ssdp = null
        http = null
    }

    /**
     * Accepts every command and only reports it, for trying the protocol against real apps
     * before playback is wired up.
     */
    class LoggingTarget : DlnaRenderer.Target {
        @Volatile private var status = DlnaRenderer.Status(DlnaState.STOPPED)

        override fun open(url: String, title: String?) {
            Log.i(TAG, "Media: ${title ?: "(no title)"} · $url")
            status = DlnaRenderer.Status(DlnaState.STOPPED, volume = status.volume)
        }
        override fun play() { status = status.copy(state = DlnaState.PLAYING) }
        override fun pause() { status = status.copy(state = DlnaState.PAUSED) }
        override fun stop() { status = status.copy(state = DlnaState.STOPPED, positionSec = 0.0) }
        override fun seek(positionSec: Double) { status = status.copy(positionSec = positionSec) }
        override fun setVolume(percent: Int) { status = status.copy(volume = percent) }
        override fun setMuted(muted: Boolean) { status = status.copy(muted = muted) }
        override fun status() = status
    }

    private companion object {
        const val TAG = "AndroPlayDlna"
        const val KEY_UUID = "uuid"
    }
}
