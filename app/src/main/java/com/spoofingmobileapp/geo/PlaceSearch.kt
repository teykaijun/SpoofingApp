package com.spoofingmobileapp.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Place(val label: String, val position: LatLng)

/** Finds places by name with the platform [Geocoder], or parses typed coordinates directly. */
class PlaceSearch(private val context: Context) {

    class UnavailableException : Exception("No geocoder available on this device")

    /**
     * @throws UnavailableException if the device has no geocoding backend.
     * @throws IOException on network or backend failures.
     */
    suspend fun search(query: String, maxResults: Int = 5): List<Place> {
        CoordinateParser.parse(query)?.let { return listOf(Place(it.format(), it)) }
        if (!Geocoder.isPresent()) throw UnavailableException()

        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address> = withTimeout(TIMEOUT_MILLIS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, maxResults, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException(errorMessage ?: "Geocoder error"))
                            }
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, maxResults).orEmpty()
                }
            }
        }
        return addresses
            .filter { it.hasLatitude() && it.hasLongitude() }
            .map { Place(it.label(), LatLng.normalized(it.latitude, it.longitude)) }
    }

    private fun Address.label(): String {
        val lines = (0..maxAddressLineIndex).mapNotNull { getAddressLine(it) }
        return lines.joinToString(", ").ifBlank {
            listOfNotNull(featureName, locality, countryName).joinToString(", ")
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
