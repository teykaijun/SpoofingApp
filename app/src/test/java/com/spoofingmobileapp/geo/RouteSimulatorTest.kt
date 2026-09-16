package com.spoofingmobileapp.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSimulatorTest {

    private val start = LatLng(0.0, 0.0)
    private val end = LatLng(0.0, 0.01) // ~1.1 km east

    @Test
    fun singleWaypointHoldsPosition() {
        val simulator = RouteSimulator(listOf(start), speedMetersPerSecond = 0.0, loop = false)
        val sample = simulator.sampleAtDistance(500.0)
        assertEquals(start, sample.position)
        assertEquals(0.0, sample.speedMetersPerSecond, 0.0)
        assertFalse(sample.finished)
    }

    @Test
    fun movesAlongRouteWithBearingAndSpeed() {
        val simulator = RouteSimulator(listOf(start, end), speedMetersPerSecond = 10.0, loop = false)
        val sample = simulator.sampleAtDistance(simulator.totalDistanceMeters / 2)
        assertEquals(0.005, sample.position.longitude, 1e-9)
        assertEquals(90.0, sample.bearingDegrees, 1e-6)
        assertEquals(10.0, sample.speedMetersPerSecond, 0.0)
        assertFalse(sample.finished)
    }

    @Test
    fun stopsAtLastWaypointWhenNotLooping() {
        val simulator = RouteSimulator(listOf(start, end), speedMetersPerSecond = 10.0, loop = false)
        val sample = simulator.sampleAtDistance(simulator.totalDistanceMeters + 100.0)
        assertEquals(end, sample.position)
        assertEquals(0.0, sample.speedMetersPerSecond, 0.0)
        assertTrue(sample.finished)
    }

    @Test
    fun loopClosesRouteAndWrapsAround() {
        val simulator = RouteSimulator(listOf(start, end), speedMetersPerSecond = 10.0, loop = true)
        assertEquals(2 * GeoMath.distanceMeters(start, end), simulator.totalDistanceMeters, 1e-6)

        val headingBack = simulator.sampleAtDistance(simulator.totalDistanceMeters * 0.75)
        assertEquals(0.005, headingBack.position.longitude, 1e-9)
        assertEquals(270.0, headingBack.bearingDegrees, 1e-6)
        assertFalse(headingBack.finished)

        val secondLap = simulator.sampleAtDistance(simulator.totalDistanceMeters * 1.25)
        assertEquals(0.005, secondLap.position.longitude, 1e-9)
        assertEquals(90.0, secondLap.bearingDegrees, 1e-6)
    }

    @Test
    fun ignoresDuplicateWaypoints() {
        val simulator = RouteSimulator(listOf(start, start, end), speedMetersPerSecond = 10.0, loop = false)
        assertEquals(GeoMath.distanceMeters(start, end), simulator.totalDistanceMeters, 1e-6)
    }
}
