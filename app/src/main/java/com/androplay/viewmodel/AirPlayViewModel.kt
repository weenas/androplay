package com.androplay.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.androplay.service.AirPlayConnectionState
import com.androplay.service.AirPlayManager
import com.androplay.service.StreamInfo
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

    private val _state = MutableStateFlow(AirPlayUiState())
    val state: StateFlow<AirPlayUiState> = _state.asStateFlow()

    private var _navigateToSettings = MutableLiveData(false)
    val navigateToSettings: LiveData<Boolean> = _navigateToSettings

    private var _navigateBack = MutableLiveData(false)
    val navigateBack: LiveData<Boolean> = _navigateBack

    init {
        manager.registerStateCallback { state, streamInfo, error ->
            _state.value = AirPlayUiState(
                connectionState = state,
                streamInfo = streamInfo,
                errorMessage = error
            )
        }
    }

    fun startServer() {
        manager.start()
    }

    fun stopServer() {
        manager.stop()
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
        manager.unregisterStateCallback()
    }
}
