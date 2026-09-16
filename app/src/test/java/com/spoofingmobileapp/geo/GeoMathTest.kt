package com.spoofingmobileapp.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class GeoMathTest {

    @Test
    fun distanceBetweenParisAndLondon() {
        val paris = LatLng(48.8566, 2.3522)
        val london = LatLng(51.5074, -0.1278)
        assertEquals(343_500.0, GeoMath.distanceMeters(paris, london), 1_500.0)
    }

    @Test
    fun oneDegreeOfLongitudeAtEquator() {
        assertEquals(111_195.0, GeoMath.distanceMeters(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 5.0)
    }

    @Test
    fun bearingsPointToCardinalDirections() {
        val origin = LatLng(0.0, 0.0)
        assertEquals(0.0, GeoMath.initialBearingDegrees(origin, LatLng(1.0, 0.0)), 1e-9)
        assertEquals(90.0, GeoMath.initialBearingDegrees(origin, LatLng(0.0, 1.0)), 1e-9)
        assertEquals(180.0, GeoMath.initialBearingDegrees(origin, LatLng(-1.0, 0.0)), 1e-9)
        assertEquals(270.0, GeoMath.initialBearingDegrees(origin, LatLng(0.0, -1.0)), 1e-9)
    }

    @Test
    fun interpolateMidpointOnEquator() {
        val mid = GeoMath.interpolate(LatLng(0.0, 0.0), LatLng(0.0, 10.0), 0.5)
        assertEquals(0.0, mid.latitude, 1e-9)
        assertEquals(5.0, mid.longitude, 1e-9)
    }

    @Test
    fun interpolateAcrossAntimeridianTakesShortWay() {
        val mid = GeoMath.interpolate(LatLng(0.0, 179.0), LatLng(0.0, -179.0), 0.5)
        assertEquals(180.0, abs(mid.longitude), 1e-6)
    }

    @Test
    fun normalizedWrapsLongitudeAndClampsLatitude() {
        assertEquals(-170.0, LatLng.normalized(10.0, 190.0).longitude, 1e-9)
        assertEquals(90.0, LatLng.normalized(95.0, 0.0).latitude, 1e-9)
    }
}
