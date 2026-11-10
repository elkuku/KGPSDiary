package org.elkuku.kgpsdiary.track

import kotlinx.serialization.json.Json
import org.elkuku.kgpsdiary.data.TrackInput
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMathTest {
    private fun point(latitude: Double, longitude: Double, time: Long) = RecordedPoint(latitude = latitude, longitude = longitude, time = time)

    @Test
    fun measuresLikeTheServer() {
        // 0.01° along the meridian, as in the server's TrackApiTest
        assertEquals(1111.95, TrackMath.between(0.0, -78.5, 0.01, -78.5), 0.2)
    }

    @Test
    fun addsUpTheSegments() {
        val points = listOf(point(0.0, 0.0, 0), point(0.005, 0.0, 1), point(0.01, 0.0, 2))

        assertEquals(1111.95, TrackMath.distance(points), 0.2)
        assertEquals(0.0, TrackMath.distance(points.take(1)), 0.0)
    }

    @Test
    fun uploadsTheTimeAsWholeSeconds() {
        val input = TrackRecorder.trackInput("Walk", listOf(point(-0.2, -78.5, 1_793_000_000_123), point(-0.21, -78.5, 1_793_000_005_999)))

        // The server only takes whole numbers for the time
        assertEquals(
            """{"name":"Walk","points":[[-0.2,-78.5,1793000000],[-0.21,-78.5,1793000005]]}""",
            Json.encodeToString(TrackInput.serializer(), input),
        )
    }
}
