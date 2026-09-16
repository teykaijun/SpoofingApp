package com.spoofingmobileapp.spoof

import com.spoofingmobileapp.geo.LocationSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SpoofState {
    data object Idle : SpoofState
    data class Active(val config: SpoofConfig, val sample: LocationSample) : SpoofState
}

sealed interface SpoofError {
    data object NotMockLocationApp : SpoofError
    data object MissingLocationPermission : SpoofError
    data object ForegroundServiceBlocked : SpoofError
    data class Unexpected(val message: String) : SpoofError
}

/** Process-wide spoofing state shared between [MockLocationService] and the UI. */
object SpoofSession {

    private val _state = MutableStateFlow<SpoofState>(SpoofState.Idle)
    val state: StateFlow<SpoofState> = _state.asStateFlow()

    private val _error = MutableStateFlow<SpoofError?>(null)
    val error: StateFlow<SpoofError?> = _error.asStateFlow()

    internal fun update(state: SpoofState) {
        _state.value = state
    }

    internal fun reportError(error: SpoofError) {
        _error.value = error
    }

    fun clearError() {
        _error.value = null
    }
}
