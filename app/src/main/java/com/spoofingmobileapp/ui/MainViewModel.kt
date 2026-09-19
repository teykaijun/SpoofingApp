package com.spoofingmobileapp.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.spoofingmobileapp.BuildConfig
import com.spoofingmobileapp.R
import com.spoofingmobileapp.SpooferApplication
import com.spoofingmobileapp.data.Favorite
import com.spoofingmobileapp.data.SpoofSettings
import com.spoofingmobileapp.geo.CoordinateParser
import com.spoofingmobileapp.geo.LatLng
import com.spoofingmobileapp.geo.Place
import com.spoofingmobileapp.geo.PlaceSearch
import com.spoofingmobileapp.geo.Units
import com.spoofingmobileapp.spoof.MockLocationService
import com.spoofingmobileapp.spoof.MockLocationSetup
import com.spoofingmobileapp.spoof.RemoteCommand
import com.spoofingmobileapp.spoof.SpoofConfig
import com.spoofingmobileapp.spoof.SpoofError
import com.spoofingmobileapp.spoof.SpoofSession
import com.spoofingmobileapp.spoof.SpoofState
import com.spoofingmobileapp.update.UpdateInfo
import com.spoofingmobileapp.update.UpdateRepository
import com.spoofingmobileapp.update.UpdateSession
import com.spoofingmobileapp.update.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException

enum class SpoofMode { Fixed, Route }

data class SetupStatus(
    val hasLocationPermission: Boolean = true,
    val developerOptionsEnabled: Boolean = true,
    val isMockLocationApp: Boolean = true,
) {
    val isComplete: Boolean get() = hasLocationPermission && isMockLocationApp
}

enum class SearchFailure(@StringRes val message: Int) {
    NoResults(R.string.search_no_results),
    Unavailable(R.string.search_unavailable),
    Failed(R.string.search_failed),
}

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val places: List<Place>) : SearchState
    data class Error(val failure: SearchFailure) : SearchState
}

sealed interface CameraMove {
    data class Center(val position: LatLng, val zoom: Double? = null) : CameraMove
    data class Fit(val positions: List<LatLng>) : CameraMove
}

data class MainUiState(
    val mode: SpoofMode = SpoofMode.Fixed,
    val target: LatLng? = null,
    val waypoints: List<LatLng> = emptyList(),
    val search: SearchState = SearchState.Idle,
    val setup: SetupStatus = SetupStatus(),
)

/** The session the current selection would start, or null if there is nothing to spoof yet. */
fun MainUiState.toConfig(settings: SpoofSettings): SpoofConfig? {
    val isRoute = mode == SpoofMode.Route
    val points = if (isRoute) waypoints.takeIf { it.size >= 2 }.orEmpty() else listOfNotNull(target)
    if (points.isEmpty()) return null
    return SpoofConfig(
        waypoints = points,
        speedMetersPerSecond = if (isRoute) Units.kmhToMps(settings.speedKmh) else 0.0,
        loop = isRoute && settings.loopRoute,
        accuracyMeters = settings.accuracyMeters,
        updateIntervalMillis = settings.updateIntervalMillis,
        mockPlayServices = settings.mockPlayServices,
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpooferApplication
    private val placeSearch = PlaceSearch(application)
    private val updates = UpdateRepository(application)
    private var searchJob: Job? = null
    private var updateJob: Job? = null

    private val _ui = MutableStateFlow(MainUiState(target = app.settings.lastTarget))
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    val settings: StateFlow<SpoofSettings> = app.settings.settings
    val favorites: StateFlow<List<Favorite>> = app.favorites.favorites
    val spoofState: StateFlow<SpoofState> = SpoofSession.state
    val spoofError: StateFlow<SpoofError?> = SpoofSession.error
    val updateState: StateFlow<UpdateState> = UpdateSession.state

    private val _cameraMoves = MutableSharedFlow<CameraMove>(extraBufferCapacity = 1)
    val cameraMoves: SharedFlow<CameraMove> = _cameraMoves.asSharedFlow()

    /** One-off snackbar messages as string resource ids. */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    init {
        refreshSetup()
    }

    fun refreshSetup() {
        val context = getApplication<Application>()
        _ui.update {
            it.copy(
                setup = SetupStatus(
                    hasLocationPermission = MockLocationSetup.hasLocationPermission(context),
                    developerOptionsEnabled = MockLocationSetup.isDeveloperOptionsEnabled(context),
                    isMockLocationApp = MockLocationSetup.isMockLocationApp(context),
                ),
            )
        }
    }

    fun setMode(mode: SpoofMode) = _ui.update { it.copy(mode = mode) }

    fun onMapTap(position: LatLng) = addPoint(position)

    /** Sets the fixed point from typed coordinates and moves the map there. */
    fun setTarget(position: LatLng) {
        _ui.update { it.copy(mode = SpoofMode.Fixed, target = position) }
        app.settings.lastTarget = position
        _cameraMoves.tryEmit(CameraMove.Center(position))
    }

    fun undoWaypoint() = _ui.update { it.copy(waypoints = it.waypoints.dropLast(1)) }

    fun clearWaypoints() = _ui.update { it.copy(waypoints = emptyList()) }

    fun setSpeedKmh(kmh: Double) = app.settings.update { it.copy(speedKmh = kmh) }

    fun setLoopRoute(loop: Boolean) = app.settings.update { it.copy(loopRoute = loop) }

    fun updateSettings(transform: (SpoofSettings) -> SpoofSettings) = app.settings.update(transform)

    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(search = SearchState.Idle) }
            return
        }
        CoordinateParser.parse(trimmed)?.let {
            choosePlace(Place(it.format(), it))
            return
        }
        _ui.update { it.copy(search = SearchState.Loading) }
        searchJob = viewModelScope.launch {
            val result = try {
                val places = placeSearch.search(trimmed)
                if (places.isEmpty()) SearchState.Error(SearchFailure.NoResults) else SearchState.Results(places)
            } catch (e: PlaceSearch.UnavailableException) {
                SearchState.Error(SearchFailure.Unavailable)
            } catch (e: IOException) {
                SearchState.Error(SearchFailure.Failed)
            } catch (e: TimeoutCancellationException) {
                SearchState.Error(SearchFailure.Failed)
            }
            _ui.update { it.copy(search = result) }
        }
    }

    fun choosePlace(place: Place) {
        searchJob?.cancel()
        _ui.update { it.copy(search = SearchState.Idle) }
        addPoint(place.position)
        _cameraMoves.tryEmit(CameraMove.Center(place.position, zoom = 16.0))
    }

    fun dismissSearch() {
        searchJob?.cancel()
        _ui.update { it.copy(search = SearchState.Idle) }
    }

    fun saveFavorite(name: String) {
        val target = _ui.value.target ?: return
        app.favorites.add(name.ifBlank { target.format(5) }, target)
    }

    fun deleteFavorite(favorite: Favorite) = app.favorites.remove(favorite.id)

    fun chooseFavorite(favorite: Favorite) = choosePlace(Place(favorite.name, favorite.position))

    fun centerOnSelection() {
        val ui = _ui.value
        val move = when (ui.mode) {
            SpoofMode.Fixed -> ui.target?.let { CameraMove.Center(it) }
            SpoofMode.Route -> when (ui.waypoints.size) {
                0 -> null
                1 -> CameraMove.Center(ui.waypoints.first())
                else -> CameraMove.Fit(ui.waypoints)
            }
        } ?: (spoofState.value as? SpoofState.Active)?.let { CameraMove.Center(it.sample.position) }
        if (move != null) _cameraMoves.tryEmit(move)
    }

    fun centerOnDeviceLocation() {
        viewModelScope.launch {
            val position = lastKnownDevicePosition()
            if (position == null) {
                _messages.tryEmit(R.string.error_location_unavailable)
            } else {
                _cameraMoves.tryEmit(CameraMove.Center(position, zoom = 16.0))
            }
        }
    }

    fun startSpoofing() {
        val config = _ui.value.toConfig(settings.value) ?: return
        SpoofSession.clearError()
        MockLocationService.start(getApplication<Application>(), config)
    }

    fun stopSpoofing() = MockLocationService.stop(getApplication<Application>())

    fun clearError() = SpoofSession.clearError()

    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        UpdateSession.report(UpdateState.Checking)
        updateJob = viewModelScope.launch {
            try {
                val info = updates.findUpdate()
                UpdateSession.report(
                    if (info == null) UpdateState.UpToDate(BuildConfig.VERSION_NAME) else UpdateState.Available(info),
                )
            } catch (e: IOException) {
                UpdateSession.report(UpdateState.Failed(e.message))
            }
        }
    }

    /** Downloads the newer build and hands it to Android's installer. */
    fun installUpdate(info: UpdateInfo) {
        if (updateJob?.isActive == true) return
        if (!updates.canInstallPackages()) {
            UpdateSession.report(UpdateState.PermissionRequired(info))
            return
        }
        updateJob = viewModelScope.launch {
            try {
                UpdateSession.report(UpdateState.Downloading(0f))
                val apk = updates.download(info) { progress ->
                    UpdateSession.report(UpdateState.Downloading(progress))
                }
                UpdateSession.report(UpdateState.Installing)
                updates.install(apk)
            } catch (e: IOException) {
                UpdateSession.report(UpdateState.Failed(e.message))
            } catch (e: SecurityException) {
                UpdateSession.report(UpdateState.Failed(e.message))
            }
        }
    }

    fun openInstallPermissionSettings() = updates.openInstallPermissionSettings()

    fun dismissUpdate() = UpdateSession.dismiss()

    /** Applies a command sent from the desktop controller over adb. */
    fun applyRemoteCommand(command: RemoteCommand) {
        when (command) {
            RemoteCommand.Stop -> stopSpoofing()
            is RemoteCommand.Start -> {
                command.speedKmh?.let { setSpeedKmh(it) }
                command.loop?.let { setLoopRoute(it) }
                command.accuracyMeters?.let { accuracy -> updateSettings { it.copy(accuracyMeters = accuracy) } }
                if (command.waypoints.size > 1) {
                    _ui.update { it.copy(mode = SpoofMode.Route, waypoints = command.waypoints) }
                    _cameraMoves.tryEmit(CameraMove.Fit(command.waypoints))
                } else {
                    val point = command.waypoints.first()
                    _ui.update { it.copy(mode = SpoofMode.Fixed, target = point) }
                    app.settings.lastTarget = point
                    _cameraMoves.tryEmit(CameraMove.Center(point, zoom = 16.0))
                }
                startSpoofing()
            }
        }
    }

    private fun addPoint(position: LatLng) {
        when (_ui.value.mode) {
            SpoofMode.Fixed -> {
                _ui.update { it.copy(target = position) }
                app.settings.lastTarget = position
            }
            SpoofMode.Route -> _ui.update { it.copy(waypoints = it.waypoints + position) }
        }
    }

    /** Note: while spoofing is active this returns the spoofed position. */
    @SuppressLint("MissingPermission") // Guarded by hasLocationPermission().
    private suspend fun lastKnownDevicePosition(): LatLng? {
        val context = getApplication<Application>()
        if (!MockLocationSetup.hasLocationPermission(context)) return null
        val fromPlayServices: Location? = try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val location = fromPlayServices ?: context.getSystemService(LocationManager::class.java)?.let { manager ->
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }
        return location?.let { LatLng.normalized(it.latitude, it.longitude) }
    }
}
