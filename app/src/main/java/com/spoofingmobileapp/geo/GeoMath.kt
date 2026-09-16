package com.spoofingmobileapp.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Spherical-earth helpers. Accurate to ~0.5%, which is plenty for simulated movement. */
object GeoMath {

    /** Mean Earth radius (IUGG) in meters. */
    const val EARTH_RADIUS_METERS = 6_371_008.8

    fun distanceMeters(from: LatLng, to: LatLng): Double =
        EARTH_RADIUS_METERS * angularDistance(from, to)

    /** Initial great-circle bearing from [from] to [to], in degrees clockwise from north (0..360). */
    fun initialBearingDegrees(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Point [fraction] (0..1) of the way along the great circle from [from] to [to]. */
    fun interpolate(from: LatLng, to: LatLng, fraction: Double): LatLng {
        if (fraction <= 0.0) return from
        if (fraction >= 1.0) return to
        val delta = angularDistance(from, to)
        if (delta < 1e-12) return from
        // Antipodal points have no unique great circle; just jump halfway through.
        if (PI - delta < 1e-9) return if (fraction < 0.5) from else to

        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)
        val a = sin((1 - fraction) * delta) / sin(delta)
        val b = sin(fraction * delta) / sin(delta)
        val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
        val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
        val z = a * sin(lat1) + b * sin(lat2)
        return LatLng.normalized(
            Math.toDegrees(atan2(z, sqrt(x * x + y * y))),
            Math.toDegrees(atan2(y, x)),
        )
    }

    private fun angularDistance(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
