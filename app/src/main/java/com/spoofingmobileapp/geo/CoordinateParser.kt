package com.spoofingmobileapp.geo

object CoordinateParser {

    private val pattern =
        Regex("""^\s*\(?\s*([-+]?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*([-+]?\d{1,3}(?:\.\d+)?)\s*\)?\s*$""")

    /**
     * Parses decimal-degree pairs such as `37.4219, -122.0840`, `37.4219 -122.0840` or
     * `(37.4219;-122.0840)`. Returns null if [text] is not a valid coordinate pair.
     */
    fun parse(text: String): LatLng? {
        val match = pattern.matchEntire(text) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return LatLng(latitude, longitude)
    }
}
