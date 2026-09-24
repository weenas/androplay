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

    private var _navigateToSettings = MutableLiveData(false)
    val navigateToSettings: LiveData<Boolean> = _navigateToSettings

    private var _navigateBack = MutableLiveData(false)
    val navigateBack: LiveData<Boolean> = _navigateBack

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

    fun setVideoSurface(surface: Surface?) {
        manager.setVideoSurface(surface)
    }

    fun updateSettings(transform: (ReceiverSettings) -> ReceiverSettings) {
        _settings.value = transform(_settings.value)
        settingsStore.save(_settings.value)
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
        manager.unregisterStateCallback(stateCallback)
    }
}
