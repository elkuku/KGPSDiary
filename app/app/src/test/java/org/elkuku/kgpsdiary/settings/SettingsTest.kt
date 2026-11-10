package org.elkuku.kgpsdiary.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private class MemoryStore(var settings: AppSettings = AppSettings()) : SettingsStore {
        override fun load() = settings

        override fun save(settings: AppSettings) {
            this.settings = settings
        }
    }

    @Test
    fun theDefaultsFollowThePhoneWithTheStandardMap() {
        assertEquals(AppSettings(ThemeMode.SYSTEM, MapStyle.STANDARD), Settings(MemoryStore()).state.value)
    }

    @Test
    fun changesAreKeptForTheNextStart() {
        val store = MemoryStore()
        val settings = Settings(store)

        settings.setTheme(ThemeMode.DARK)
        settings.setMapStyle(MapStyle.STANDARD_WITHOUT_BUSINESSES)

        val expected = AppSettings(ThemeMode.DARK, MapStyle.STANDARD_WITHOUT_BUSINESSES)
        assertEquals(expected, settings.state.value)
        assertEquals(expected, Settings(store).state.value)
    }

    @Test
    fun storedNamesAreReadAndUnknownOnesAreTheDefault() {
        assertEquals(AppSettings(ThemeMode.LIGHT, MapStyle.STANDARD_WITHOUT_BUSINESSES), AppSettings.parse("LIGHT", "STANDARD_WITHOUT_BUSINESSES"))
        assertEquals(AppSettings(), AppSettings.parse(null, null))
        assertEquals(AppSettings(), AppSettings.parse("SEPIA", "SATELLITE"))
    }

    @Test
    fun theThemeOverridesThePhoneUnlessItFollowsIt() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun onlyTheStyleWithoutBusinessesHidesThePointsOfInterest() {
        assertTrue(MapStyle.STANDARD.showPointsOfInterest)
        assertFalse(MapStyle.STANDARD_WITHOUT_BUSINESSES.showPointsOfInterest)
    }
}
