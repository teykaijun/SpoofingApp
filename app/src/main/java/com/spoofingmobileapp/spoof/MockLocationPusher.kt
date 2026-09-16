package com.spoofingmobileapp.spoof

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.spoofingmobileapp.geo.LocationSample

/**
 * Feeds simulated fixes into Android's location stack: the platform GPS and network test providers
 * (plus the platform fused provider on Android 12+), and Google Play services' fused location
 * provider in mock mode, which is what most apps actually read.
 *
 * [start] and [push] throw [SecurityException] if this app is not the selected mock location app.
 */
@SuppressLint("MissingPermission") // MockLocationService only starts once location permission is granted.
class MockLocationPusher(context: Context) {

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val activeProviders = mutableListOf<String>()

    @Volatile
    private var playServicesMockMode = false

    @Synchronized
    fun start(mockPlayServices: Boolean) {
        if (activeProviders.isEmpty()) {
            for (name in PROVIDERS) {
                removeLeftoverProvider(name)
                addTestProvider(name)
                locationManager.setTestProviderEnabled(name, true)
                activeProviders += name
            }
        }
        if (mockPlayServices != playServicesMockMode) {
            playServicesMockMode = mockPlayServices
            fusedClient.setMockMode(mockPlayServices).addOnFailureListener { error ->
                Log.w(TAG, "Play services mock mode unavailable", error)
                playServicesMockMode = false
            }
        }
    }

    @Synchronized
    fun push(sample: LocationSample, accuracyMeters: Float) {
        for (name in activeProviders) {
            locationManager.setTestProviderLocation(name, sample.toLocation(name, accuracyMeters))
        }
        if (playServicesMockMode) {
            fusedClient.setMockLocation(sample.toLocation(FUSED_PROVIDER, accuracyMeters))
        }
    }

    /** Restores the real providers. Safe to call repeatedly. */
    @Synchronized
    fun stop() {
        for (name in activeProviders) {
            try {
                locationManager.setTestProviderEnabled(name, false)
                locationManager.removeTestProvider(name)
            } catch (e: RuntimeException) {
                Log.d(TAG, "Could not remove test provider $name", e)
            }
        }
        activeProviders.clear()
        if (playServicesMockMode) {
            playServicesMockMode = false
            fusedClient.setMockMode(false)
        }
    }

    /**
     * Removes test providers left behind if the app was killed mid-session. Does nothing when this
     * app is not the selected mock location app.
     */
    @Synchronized
    fun removeLeftovers() {
        if (activeProviders.isNotEmpty()) return
        for (name in PROVIDERS) {
            try {
                removeLeftoverProvider(name)
            } catch (e: SecurityException) {
                return
            }
        }
    }

    private fun removeLeftoverProvider(name: String) {
        try {
            locationManager.removeTestProvider(name)
        } catch (e: IllegalArgumentException) {
            // Older Android versions throw when no test provider exists; nothing to remove.
        }
    }

    private fun addTestProvider(name: String) {
        val requiresNetwork = name == LocationManager.NETWORK_PROVIDER
        val requiresSatellite = name == LocationManager.GPS_PROVIDER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setHasNetworkRequirement(requiresNetwork)
                .setHasSatelliteRequirement(requiresSatellite)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build()
            locationManager.addTestProvider(name, properties)
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                name, requiresNetwork, requiresSatellite, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
            )
        }
    }

    private fun LocationSample.toLocation(provider: String, accuracyMeters: Float) = Location(provider).apply {
        latitude = position.latitude
        longitude = position.longitude
        accuracy = accuracyMeters
        speed = speedMetersPerSecond.toFloat()
        if (speedMetersPerSecond > 0.0) {
            bearing = bearingDegrees.toFloat()
            bearingAccuracyDegrees = 5f
            speedAccuracyMetersPerSecond = 0.5f
        }
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private companion object {
        const val TAG = "MockLocationPusher"
        const val FUSED_PROVIDER = "fused"

        val PROVIDERS: List<String> = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }
    }
}
