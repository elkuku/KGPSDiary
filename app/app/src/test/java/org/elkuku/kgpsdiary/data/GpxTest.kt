package org.elkuku.kgpsdiary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {
    private val track = Track(
        id = 4,
        name = "Walk <to> \"the\" market & back",
        points = listOf(listOf(-0.2147, -78.5075, 1_793_000_000.0), listOf(-0.21523456, -78.5077, 1_793_000_002.0)),
    )

    @Test
    fun writesAGpxTrack() {
        assertEquals(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="KGPSDiary" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <name>Walk &lt;to&gt; &quot;the&quot; market &amp; back</name>
                <trkseg>
                  <trkpt lat="-0.214700" lon="-78.507500"><time>2026-10-26T07:33:20Z</time></trkpt>
                  <trkpt lat="-0.215235" lon="-78.507700"><time>2026-10-26T07:33:22Z</time></trkpt>
                </trkseg>
              </trk>
            </gpx>

            """.trimIndent(),
            Gpx.of(track),
        )
    }

    @Test
    fun skipsIncompletePoints() {
        val gpx = Gpx.of(track.copy(points = listOf(listOf(1.0, 2.0), listOf(1.0, 2.0, 1_793_000_000.0))))

        assertEquals(1, Regex("<trkpt").findAll(gpx).count())
    }

    @Test
    fun namesTheFileAfterTheTrack() {
        assertEquals("Walk_to_the_market_back.gpx", Gpx.fileName(track))
        assertEquals("Mañana_en_Quito.gpx", Gpx.fileName(track.copy(name = "Mañana en Quito")))
        assertEquals("track-4.gpx", Gpx.fileName(track.copy(name = " / ")))
        assertTrue(Gpx.fileName(track.copy(name = "x".repeat(200))).length <= 64)
    }
}
