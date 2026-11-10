package org.elkuku.kgpsdiary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchWaypointsTest {
    private val basilica = Waypoint(
        1,
        "Basílica del Voto Nacional",
        latitude = 0.0,
        longitude = 0.0,
        category = Category(2, "Monuments"),
        tags = listOf(Tag(7, "Ecuador")),
    )
    private val market = Waypoint(2, "Mercado Central", description = "Lunch at the market", latitude = 0.0, longitude = 0.0)
    private val all = listOf(basilica, market)

    private fun titles(query: String) = searchWaypoints(all, query).map { it.title }

    @Test
    fun anEmptySearchFindsAll() {
        assertEquals(all, searchWaypoints(all, "  "))
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(listOf("Basílica del Voto Nacional"), titles("BASILICA"))
        assertEquals(listOf("Basílica del Voto Nacional"), titles("basílica"))
    }

    @Test
    fun searchesDescriptionCategoryAndTags() {
        assertEquals(listOf("Mercado Central"), titles("lunch"))
        assertEquals(listOf("Basílica del Voto Nacional"), titles("monuments"))
        assertEquals(listOf("Basílica del Voto Nacional"), titles("ecuador"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf("Basílica del Voto Nacional"), titles("voto ecuador"))
        assertEquals(emptyList<String>(), titles("voto lunch"))
    }
}
