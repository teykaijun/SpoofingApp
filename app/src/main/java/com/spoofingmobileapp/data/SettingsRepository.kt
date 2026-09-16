package com.spoofingmobileapp.data

import android.content.Context
import androidx.core.content.edit
import com.spoofingmobileapp.geo.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

data class SpoofSettings(
    val accuracyMeters: Float = 5f,
    val updateIntervalMillis: Long = 1_000L,
    val mockPlayServices: Boolean = true,
    val speedKmh: Double = 5.0,
    val loopRoute: Boolean = false,
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SpoofSettings> = _settings.asStateFlow()

    fun update(transform: (SpoofSettings) -> SpoofSettings) {
        val updated = _settings.updateAndGet(transform)
        prefs.edit {
            putFloat(KEY_ACCURACY, updated.accuracyMeters)
            putLong(KEY_INTERVAL, updated.updateIntervalMillis)
            putBoolean(KEY_PLAY_SERVICES, updated.mockPlayServices)
            putLong(KEY_SPEED, updated.speedKmh.toRawBits())
            putBoolean(KEY_LOOP, updated.loopRoute)
        }
    }

    /** The last fixed point the user picked, used to center the map on the next launch. */
    var lastTarget: LatLng?
        get() {
            if (!prefs.contains(KEY_TARGET_LAT) || !prefs.contains(KEY_TARGET_LNG)) return null
            return runCatching {
                LatLng(
                    Double.fromBits(prefs.getLong(KEY_TARGET_LAT, 0L)),
                    Double.fromBits(prefs.getLong(KEY_TARGET_LNG, 0L)),
                )
            }.getOrNull()
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_TARGET_LAT)
                remove(KEY_TARGET_LNG)
            } else {
                putLong(KEY_TARGET_LAT, value.latitude.toRawBits())
                putLong(KEY_TARGET_LNG, value.longitude.toRawBits())
            }
        }

    private fun load(): SpoofSettings {
        val defaults = SpoofSettings()
        return SpoofSettings(
            accuracyMeters = prefs.getFloat(KEY_ACCURACY, defaults.accuracyMeters),
            updateIntervalMillis = prefs.getLong(KEY_INTERVAL, defaults.updateIntervalMillis),
            mockPlayServices = prefs.getBoolean(KEY_PLAY_SERVICES, defaults.mockPlayServices),
            speedKmh = if (prefs.contains(KEY_SPEED)) {
                Double.fromBits(prefs.getLong(KEY_SPEED, 0L))
            } else {
                defaults.speedKmh
            },
            loopRoute = prefs.getBoolean(KEY_LOOP, defaults.loopRoute),
        )
    }

    private companion object {
        const val KEY_ACCURACY = "accuracy_m"
        const val KEY_INTERVAL = "interval_ms"
        const val KEY_PLAY_SERVICES = "mock_play_services"
        const val KEY_SPEED = "speed_kmh"
        const val KEY_LOOP = "loop_route"
        const val KEY_TARGET_LAT = "target_lat"
        const val KEY_TARGET_LNG = "target_lng"
    }
}
