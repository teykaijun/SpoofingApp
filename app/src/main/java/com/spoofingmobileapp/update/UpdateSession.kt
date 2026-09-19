package com.spoofingmobileapp.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState

    /** Android confirmed the install; the app is usually restarted right after. */
    data object Installed : UpdateState
    data class Failed(val message: String?) : UpdateState

    /** The user has not allowed this app to install apps yet. */
    data class PermissionRequired(val info: UpdateInfo) : UpdateState
}

/**
 * Update progress, shared between the view model and [InstallResultReceiver], which Android
 * calls from outside the app's own coroutines.
 */
object UpdateSession {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun report(state: UpdateState) {
        _state.value = state
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
