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
}
