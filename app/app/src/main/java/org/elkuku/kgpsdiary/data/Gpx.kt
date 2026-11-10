package org.elkuku.kgpsdiary.data

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A track as GPX 1.1, which map apps and GPS tools read. */
object Gpx {
    fun of(track: Track): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"KGPSDiary\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n")
        append("    <name>").append(escape(track.name)).append("</name>\n")
        append("    <trkseg>\n")
        track.points.filter { it.size >= 3 }.forEach { (latitude, longitude, time) ->
            append(String.format(Locale.ROOT, "      <trkpt lat=\"%.6f\" lon=\"%.6f\">", latitude, longitude))
            append("<time>").append(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(time.toLong()))).append("</time>")
            append("</trkpt>\n")
        }
        append("    </trkseg>\n")
        append("  </trk>\n")
        append("</gpx>\n")
    }

    /** A file name from the track's name: letters, digits, dashes and underscores only. */
    fun fileName(track: Track): String {
        val base = track.name.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(60)
        return "${base.ifEmpty { "track-${track.id}" }}.gpx"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
