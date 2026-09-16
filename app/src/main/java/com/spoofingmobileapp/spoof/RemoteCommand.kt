package com.spoofingmobileapp.spoof

import android.content.Intent
import com.spoofingmobileapp.geo.CoordinateParser
import com.spoofingmobileapp.geo.LatLng

/**
 * A command sent from the desktop controller over adb, for example:
 *
 * ```
 * adb shell am start -n com.spoofingmobileapp/.MainActivity \
 *   -a com.spoofingmobileapp.action.REMOTE --es command start \
 *   --es points "48.8584,2.2945;48.8606,2.3376" --es speed 18 --es loop true
 * ```
 *
 * Everything is a string extra because those are the only kind `adb shell am` passes reliably.
 */
sealed interface RemoteCommand {

    data class Start(
        val waypoints: List<LatLng>,
        val speedKmh: Double?,
        val loop: Boolean?,
        val accuracyMeters: Float?,
    ) : RemoteCommand

    data object Stop : RemoteCommand

    companion object {
        const val ACTION = "com.spoofingmobileapp.action.REMOTE"

        fun parse(intent: Intent?): RemoteCommand? {
            if (intent == null || intent.action != ACTION) return null
            return when (intent.getStringExtra("command")?.lowercase()) {
                "stop" -> Stop
                "start" -> {
                    val waypoints = parsePoints(intent.getStringExtra("points"))
                    if (waypoints.isEmpty()) {
                        null
                    } else {
                        Start(
                            waypoints = waypoints,
                            speedKmh = intent.getStringExtra("speed")?.toDoubleOrNull()?.takeIf { it > 0 },
                            loop = intent.getStringExtra("loop")?.toBooleanStrictOrNull(),
                            accuracyMeters = intent.getStringExtra("accuracy")?.toFloatOrNull()?.takeIf { it > 0 },
                        )
                    }
                }
                else -> null
            }
        }

        /** Parses `lat,lng;lat,lng;...`, ignoring anything that is not a valid pair. */
        fun parsePoints(raw: String?): List<LatLng> =
            raw.orEmpty().split(';').mapNotNull { CoordinateParser.parse(it) }
    }
}
