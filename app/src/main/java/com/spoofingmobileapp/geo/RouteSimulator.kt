package com.spoofingmobileapp.geo

/** A simulated fix: where the device is, which way it is heading and how fast. */
data class LocationSample(
    val position: LatLng,
    val bearingDegrees: Double,
    val speedMetersPerSecond: Double,
    /** Distance covered in the current lap. */
    val distanceMeters: Double,
    val totalDistanceMeters: Double,
    /** True once a non-looping route has reached its last waypoint. */
    val finished: Boolean,
)

/**
 * Moves along [waypoints] at [speedMetersPerSecond]. A single waypoint (or zero speed) holds that
 * position. With [loop], the route is closed back to the first waypoint and repeats forever.
 */
class RouteSimulator(
    waypoints: List<LatLng>,
    private val speedMetersPerSecond: Double,
    private val loop: Boolean,
) {
    private class Leg(val start: LatLng, val end: LatLng, val length: Double, val startOffset: Double)

    private val origin: LatLng
    private val legs: List<Leg>

    val totalDistanceMeters: Double

    init {
        require(waypoints.isNotEmpty()) { "At least one waypoint is required" }
        require(speedMetersPerSecond >= 0.0) { "Speed must not be negative" }
        origin = waypoints.first()
        val stops = if (loop && waypoints.size > 1) waypoints + waypoints.first() else waypoints
        var offset = 0.0
        legs = stops.zipWithNext().mapNotNull { (start, end) ->
            val length = GeoMath.distanceMeters(start, end)
            if (length < MIN_LEG_METERS) {
                null
            } else {
                Leg(start, end, length, offset).also { offset += length }
            }
        }
        totalDistanceMeters = offset
    }

    val isStationary: Boolean get() = legs.isEmpty() || speedMetersPerSecond == 0.0

    /** Sample after travelling [traveledMeters] from the start of the route. */
    fun sampleAtDistance(traveledMeters: Double): LocationSample {
        if (isStationary) {
            return LocationSample(origin, 0.0, 0.0, 0.0, totalDistanceMeters, finished = false)
        }
        val traveled = traveledMeters.coerceAtLeast(0.0)
        if (!loop && traveled >= totalDistanceMeters) {
            val last = legs.last()
            val arrivalBearing = (GeoMath.initialBearingDegrees(last.end, last.start) + 180.0) % 360.0
            return LocationSample(
                last.end, arrivalBearing, 0.0, totalDistanceMeters, totalDistanceMeters, finished = true,
            )
        }

        val distance = if (loop) traveled % totalDistanceMeters else traveled
        val leg = legs.last { it.startOffset <= distance }
        val fraction = ((distance - leg.startOffset) / leg.length).coerceIn(0.0, 1.0)
        val position = GeoMath.interpolate(leg.start, leg.end, fraction)
        val bearing = if (GeoMath.distanceMeters(position, leg.end) > MIN_LEG_METERS) {
            GeoMath.initialBearingDegrees(position, leg.end)
        } else {
            GeoMath.initialBearingDegrees(leg.start, leg.end)
        }
        return LocationSample(
            position, bearing, speedMetersPerSecond, distance, totalDistanceMeters, finished = false,
        )
    }

    private companion object {
        const val MIN_LEG_METERS = 0.5
    }
}
