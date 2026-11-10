package org.elkuku.kgpsdiary.ui.tracks

import java.time.Duration
import org.elkuku.kgpsdiary.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackSummaryTest {
    @Test
    fun summarizesDistanceAndDuration() {
        val summary = TrackSummary.of(
            Track(1, "Walk", startedAt = "2026-11-10T22:16:53+00:00", endedAt = "2026-11-10T23:19:24+00:00", distance = 2385.0),
        )

        assertEquals(2.385, summary.kilometers, 0.0001)
        assertEquals(Duration.ofSeconds(3751), summary.duration)
        assertEquals(2026, summary.startedAt?.year)
    }

    @Test
    fun copesWithMissingTimes() {
        val summary = TrackSummary.of(Track(1, "Walk"))

        assertNull(summary.startedAt)
        assertEquals(Duration.ZERO, summary.duration)
    }
}
