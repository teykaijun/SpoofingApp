package com.spoofingmobileapp.geo

import java.util.Locale

object Units {

    fun kmhToMps(kmh: Double): Double = kmh / 3.6

    fun mpsToKmh(metersPerSecond: Double): Double = metersPerSecond * 3.6

    fun formatDistance(meters: Double): String =
        if (meters < 1000) {
            String.format(Locale.getDefault(), "%.0f m", meters)
        } else {
            String.format(Locale.getDefault(), "%.2f km", meters / 1000)
        }

    fun formatSpeed(metersPerSecond: Double): String =
        String.format(Locale.getDefault(), "%.0f km/h", mpsToKmh(metersPerSecond))
}
