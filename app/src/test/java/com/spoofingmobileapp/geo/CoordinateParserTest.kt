package com.spoofingmobileapp.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun parsesCommaSeparatedPair() {
        assertEquals(LatLng(37.4219, -122.084), CoordinateParser.parse("37.4219, -122.084"))
        assertEquals(LatLng(37.4219, -122.084), CoordinateParser.parse("37.4219,-122.084"))
    }

    @Test
    fun parsesSpaceSeparatedAndParenthesizedPair() {
        assertEquals(LatLng(-33.8688, 151.2093), CoordinateParser.parse("(-33.8688 151.2093)"))
    }

    @Test
    fun rejectsOutOfRangeValuesAndPlainText() {
        assertNull(CoordinateParser.parse("91, 0"))
        assertNull(CoordinateParser.parse("0, 181"))
        assertNull(CoordinateParser.parse("Eiffel Tower"))
        assertNull(CoordinateParser.parse("12.5"))
    }
}
