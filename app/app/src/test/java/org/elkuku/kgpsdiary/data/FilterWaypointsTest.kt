package org.elkuku.kgpsdiary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterWaypointsTest {
    private val food = Category(1, "Food")
    private val monuments = Category(2, "Monuments")
    private val lunch = Tag(7, "Lunch")
    private val ecuador = Tag(8, "Ecuador")

    private val cafe = Waypoint(1, "Café", latitude = 0.0, longitude = 0.0, category = food, tags = listOf(lunch, ecuador))
    private val mitad = Waypoint(2, "Mitad del Mundo", latitude = 0.0, longitude = 0.0, category = monuments, tags = listOf(ecuador))
    private val home = Waypoint(3, "Home", latitude = 0.0, longitude = 0.0)
    private val all = listOf(cafe, mitad, home)

    @Test
    fun withoutFiltersAllAreShown() {
        assertEquals(all, filterWaypoints(all, null, null))
    }

    @Test
    fun filtersByCategory() {
        assertEquals(listOf(mitad), filterWaypoints(all, monuments.id, null))
    }

    @Test
    fun filtersByTag() {
        assertEquals(listOf(cafe, mitad), filterWaypoints(all, null, ecuador.id))
    }

    @Test
    fun bothFiltersApply() {
        assertEquals(listOf(cafe), filterWaypoints(all, food.id, ecuador.id))
        assertEquals(emptyList<Waypoint>(), filterWaypoints(all, monuments.id, lunch.id))
    }
}
