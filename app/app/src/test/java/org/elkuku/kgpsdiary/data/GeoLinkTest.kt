package org.elkuku.kgpsdiary.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoLinkTest {
    @Test
    fun buildsAGeoUriWithAMarker() {
        assertEquals("geo:-0.214700,-78.507500?q=-0.214700,-78.507500", GeoLink.uri(-0.2147, -78.5075))
    }

    @Test
    fun sharesOnlyTheCoordinates() {
        assertEquals("-0.214700, -78.507500\ngeo:-0.214700,-78.507500?q=-0.214700,-78.507500", GeoLink.shareText(-0.2147, -78.5075))
    }

    @Test
    fun usesADecimalPointInEveryLanguage() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("geo:52.520008,13.404954?q=52.520008,13.404954", GeoLink.uri(52.520008, 13.404954))
        } finally {
            Locale.setDefault(default)
        }
    }
}
