package com.androplay.viewmodel

import android.app.Application
import android.content.Intent
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.androplay.service.AirPlayConnectionState
import com.androplay.service.AirPlayManager
import com.androplay.service.StreamInfo
import com.androplay.service.ReceiverSettings
import com.androplay.service.ReceiverSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AirPlayUiState(
    val connectionState: AirPlayConnectionState = AirPlayConnectionState.Idle,
    val streamInfo: StreamInfo = StreamInfo(),
    val errorMessage: String? = null
)

class AirPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = AirPlayManager.getInstance(application)
    private val settingsStore = ReceiverSettingsStore(application)

    private val _state = MutableStateFlow(AirPlayUiState())
    val state: StateFlow<AirPlayUiState> = _state.asStateFlow()
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<ReceiverSettings> = _settings.asStateFlow()

    private val networkMonitor = com.androplay.service.NetworkMonitor(application).also { it.start() }
    /** The TV's network (type, Wi-Fi name, IP) for the home screen. */
    val network: StateFlow<com.androplay.service.NetworkStatus> = networkMonitor.status
    val appVersion: String = com.androplay.BuildConfig.VERSION_NAME

    fun canReadWifiName() = networkMonitor.canReadSsid()

    /** How mirroring is offered with [settings] on this TV, e.g. "H.265, up to 4K". */
    fun mirroringProfile(settings: ReceiverSettings) = manager.mirroringProfile(settings)

    /** Call after the location permission was granted, so the Wi-Fi name appears. */
    fun refreshNetwork() = networkMonitor.refresh()

    // StateFlow, not LiveData: Compose only recomposes for state it observes.
    private val _navigateToSettings = MutableStateFlow(false)
    val navigateToSettings: StateFlow<Boolean> = _navigateToSettings.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private val stateCallback: (AirPlayConnectionState, StreamInfo, String?) -> Unit = { state, streamInfo, error ->
        _state.value = AirPlayUiState(state, streamInfo, error)
    }

    init {
        manager.registerStateCallback(stateCallback)
    }

    fun startServer() {
        val intent = Intent(getApplication(), com.androplay.service.AirPlayService::class.java)
            .setAction(com.androplay.service.AirPlayService.ACTION_START)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopServer() {
        getApplication<Application>().startService(
            Intent(getApplication(), com.androplay.service.AirPlayService::class.java)
                .setAction(com.androplay.service.AirPlayService.ACTION_STOP)
        )
        manager.stop()
    }

    fun remoteControl(command: com.androplay.service.DacpClient.Command) = manager.remoteControl(command)

    fun skipMusic(forward: Boolean) = manager.skipMusic(forward)

    fun toggleVideoPause() = manager.toggleVideoPause()

    private val lyricsClient = com.androplay.service.LyricsClient()

    /** Synced lyrics for a song, or null; looked up online, so call off the main thread. */
    fun findLyrics(title: String, artist: String?, album: String?, durationSec: Double) =
        lyricsClient.find(title, artist, album, durationSec)

    /** Stops the current cast from the TV; the receiver keeps waiting for the next one. */
    fun endCasting() = manager.endCasting()

    /** For the stats overlay; main thread. */
    fun playbackStats() = manager.playbackStats()

    fun seekVideoBy(deltaSec: Int) = manager.seekVideoBy(deltaSec)

    /** The AirPlay video player, if one is active. Main thread only. */
    val videoPlayer get() = manager.videoPlayer

    fun setVideoSurface(surface: Surface?) {
        manager.setVideoSurface(surface)
    }

    fun updateSettings(transform: (ReceiverSettings) -> ReceiverSettings) {
        val previous = _settings.value
        val updated = transform(previous)
        if (updated == previous) return
        _settings.value = updated
        settingsStore.save(updated)
        // Only protocol settings need the receiver restarted (not start-on-boot or the overlay).
        if (updated.needsRestartComparedTo(previous)) manager.restartIfRunning(updated)
    }

    fun navigateToSettings() {
        _navigateToSettings.value = true
    }

    fun navigateBack() {
        _navigateBack.value = true
    }

    fun onNavigateToSettingsConsumed() {
        _navigateToSettings.value = false
    }

    fun onNavigateBackConsumed() {
        _navigateBack.value = false
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.stop()
        manager.unregisterStateCallback(stateCallback)
    }
}
