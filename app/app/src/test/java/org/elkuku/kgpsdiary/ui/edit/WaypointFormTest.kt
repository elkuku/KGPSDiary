package org.elkuku.kgpsdiary.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointFormTest {
    private val valid = WaypointForm(title = "Mitad del Mundo", latitude = "-0.0022", longitude = "-78.4558")

    @Test
    fun aTitleAndCoordinatesAreEnough() {
        assertTrue(valid.isValid)
        assertEquals(-0.0022, valid.latitudeValue)
    }

    @Test
    fun acceptsADecimalComma() {
        assertEquals(-78.4558, valid.copy(longitude = "-78,4558").longitudeValue)
    }

    @Test
    fun rejectsMissingOrOutOfRangeValues() {
        assertFalse(valid.copy(title = "  ").isValid)
        assertNull(valid.copy(latitude = "91").latitudeValue)
        assertNull(valid.copy(longitude = "-180.5").longitudeValue)
        assertNull(valid.copy(latitude = "north").latitudeValue)
        assertFalse(valid.copy(latitude = "").isValid)
    }

    @Test
    fun theMapSetsThePositionWithSixDecimals() {
        val moved = valid.at(-0.18065432, -78.4678)

        assertEquals("-0.180654", moved.latitude)
        assertEquals("-78.467800", moved.longitude)
        assertEquals(-0.180654, moved.latitudeValue)
        assertTrue(moved.isValid)
    }

    @Test
    fun aTagIsSelectedAndDeselected() {
        val tagged = valid.toggleTag(7).toggleTag(9)
        assertEquals(setOf(7, 9), tagged.tagIds)
        assertEquals(setOf(9), tagged.toggleTag(7).tagIds)
    }
}
