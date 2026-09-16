package com.spoofingmobileapp.spoof

import android.content.Intent
import com.spoofingmobileapp.geo.LatLng

/** Everything [MockLocationService] needs to run a spoofing session. */
data class SpoofConfig(
    /** One waypoint spoofs a fixed point; two or more simulate a route. */
    val waypoints: List<LatLng>,
    val speedMetersPerSecond: Double,
    val loop: Boolean,
    val accuracyMeters: Float,
    val updateIntervalMillis: Long,
    val mockPlayServices: Boolean,
) {
    init {
        require(waypoints.isNotEmpty()) { "At least one waypoint is required" }
    }

    val isRoute: Boolean get() = waypoints.size > 1

    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_LATITUDES, waypoints.map { it.latitude }.toDoubleArray())
        .putExtra(EXTRA_LONGITUDES, waypoints.map { it.longitude }.toDoubleArray())
        .putExtra(EXTRA_SPEED, speedMetersPerSecond)
        .putExtra(EXTRA_LOOP, loop)
        .putExtra(EXTRA_ACCURACY, accuracyMeters)
        .putExtra(EXTRA_INTERVAL, updateIntervalMillis)
        .putExtra(EXTRA_PLAY_SERVICES, mockPlayServices)

    companion object {
        private const val EXTRA_LATITUDES = "com.spoofingmobileapp.extra.LATITUDES"
        private const val EXTRA_LONGITUDES = "com.spoofingmobileapp.extra.LONGITUDES"
        private const val EXTRA_SPEED = "com.spoofingmobileapp.extra.SPEED"
        private const val EXTRA_LOOP = "com.spoofingmobileapp.extra.LOOP"
        private const val EXTRA_ACCURACY = "com.spoofingmobileapp.extra.ACCURACY"
        private const val EXTRA_INTERVAL = "com.spoofingmobileapp.extra.INTERVAL"
        private const val EXTRA_PLAY_SERVICES = "com.spoofingmobileapp.extra.PLAY_SERVICES"

        fun fromIntent(intent: Intent): SpoofConfig? {
            val latitudes = intent.getDoubleArrayExtra(EXTRA_LATITUDES) ?: return null
            val longitudes = intent.getDoubleArrayExtra(EXTRA_LONGITUDES) ?: return null
            if (latitudes.isEmpty() || latitudes.size != longitudes.size) return null
            return runCatching {
                SpoofConfig(
                    waypoints = latitudes.indices.map { LatLng(latitudes[it], longitudes[it]) },
                    speedMetersPerSecond = intent.getDoubleExtra(EXTRA_SPEED, 0.0),
                    loop = intent.getBooleanExtra(EXTRA_LOOP, false),
                    accuracyMeters = intent.getFloatExtra(EXTRA_ACCURACY, 5f),
                    updateIntervalMillis = intent.getLongExtra(EXTRA_INTERVAL, 1_000L),
                    mockPlayServices = intent.getBooleanExtra(EXTRA_PLAY_SERVICES, true),
                )
            }.getOrNull()
        }
    }
}
