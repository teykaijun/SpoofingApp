package com.spoofingmobileapp.geo

import java.util.Locale

/** A WGS84 coordinate in decimal degrees. */
data class LatLng(val latitude: Double, val longitude: Double) {

    init {
        require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
    }

    /** Always uses a dot as decimal separator so the text can be pasted back into search. */
    fun format(decimals: Int = 6): String =
        String.format(Locale.US, "%.${decimals}f, %.${decimals}f", latitude, longitude)

    companion object {
        /** Clamps latitude and wraps longitude into range, e.g. for points picked on a wrapped map. */
        fun normalized(latitude: Double, longitude: Double): LatLng {
            val lat = latitude.coerceIn(-90.0, 90.0)
            var lon = (longitude + 180.0) % 360.0
            if (lon < 0) lon += 360.0
            return LatLng(lat, lon - 180.0)
        }
    }
}
