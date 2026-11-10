package org.elkuku.kgpsdiary.data

import java.util.Locale

/**
 * A waypoint's position for sharing: only the coordinates, never its title or description.
 */
object GeoLink {
    /** `geo:` URI (RFC 5870) that any map app on Android opens; `q` sets a marker there. */
    fun uri(latitude: Double, longitude: Double): String {
        val position = coordinates(latitude, longitude, separator = ",")
        return "geo:$position?q=$position"
    }

    /** The coordinates with 6 decimals (about 10 cm), always with a decimal point. */
    fun coordinates(latitude: Double, longitude: Double, separator: String = ", "): String =
        String.format(Locale.ROOT, "%.6f%s%.6f", latitude, separator, longitude)

    /** The text to share: readable coordinates, and the link for map apps. */
    fun shareText(latitude: Double, longitude: Double): String =
        "${coordinates(latitude, longitude)}\n${uri(latitude, longitude)}"
}
