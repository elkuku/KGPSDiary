package org.elkuku.kgpsdiary.track

import org.elkuku.kgpsdiary.data.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackVisibilityTest {
    private class MemoryStore(var ids: Set<Int> = emptySet()) : HiddenTrackStore {
        override fun load() = ids

        override fun save(ids: Set<Int>) {
            this.ids = ids
        }
    }

    private val tracks = listOf(Track(1, "Walk"), Track(2, "Hike"), Track(3, "Ride"))

    @Test
    fun allTracksAreShownAtFirst() {
        val visibility = TrackVisibility(MemoryStore())

        assertEquals(tracks, TrackVisibility.visible(tracks, visibility.hidden.value))
    }

    @Test
    fun hiddenTracksAreLeftOutAndRemembered() {
        val store = MemoryStore()
        val visibility = TrackVisibility(store)

        visibility.toggle(2)

        assertEquals(listOf("Walk", "Ride"), TrackVisibility.visible(tracks, visibility.hidden.value).map { it.name })
        assertEquals(setOf(2), store.ids)
        // After a restart
        assertEquals(setOf(2), TrackVisibility(store).hidden.value)
    }

    @Test
    fun togglingAgainShowsTheTrack() {
        val store = MemoryStore(setOf(2, 3))
        val visibility = TrackVisibility(store)

        visibility.toggle(2)
        visibility.setVisible(3, true)

        assertEquals(emptySet<Int>(), visibility.hidden.value)
        assertEquals(emptySet<Int>(), store.ids)
    }
}
