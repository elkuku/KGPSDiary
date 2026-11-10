package org.elkuku.kgpsdiary.track

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Distances on the earth, as the server measures them. */
object TrackMath {
    private const val EARTH_RADIUS = 6_371_008.8

    /** Meters between two points (great circle). */
    fun between(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
        val phi1 = Math.toRadians(latitude1)
        val phi2 = Math.toRadians(latitude2)
        val a = sin((phi2 - phi1) / 2).let { it * it } +
            cos(phi1) * cos(phi2) * sin(Math.toRadians(longitude2 - longitude1) / 2).let { it * it }
        return 2 * EARTH_RADIUS * asin(min(1.0, sqrt(a)))
    }

    /** Meters along the points. */
    fun distance(points: List<RecordedPoint>): Double =
        points.zipWithNext { a, b -> between(a.latitude, a.longitude, b.latitude, b.longitude) }.sum()
}
