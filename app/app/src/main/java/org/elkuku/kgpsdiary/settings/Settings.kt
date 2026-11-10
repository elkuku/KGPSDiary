package org.elkuku.kgpsdiary.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Light or dark, or as the phone is set. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Mapbox's Standard style, with or without the shops, restaurants and other businesses. */
enum class MapStyle(val showPointsOfInterest: Boolean) {
    STANDARD(true),
    STANDARD_WITHOUT_BUSINESSES(false),
}

data class AppSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val mapStyle: MapStyle = MapStyle.STANDARD) {
    companion object {
        /** From the stored names; what is missing or unknown (e.g. from a newer version) is the default. */
        fun parse(theme: String?, mapStyle: String?) = AppSettings(
            theme = ThemeMode.entries.find { it.name == theme } ?: ThemeMode.SYSTEM,
            mapStyle = MapStyle.entries.find { it.name == mapStyle } ?: MapStyle.STANDARD,
        )
    }
}

/** Where the settings are kept. */
interface SettingsStore {
    fun load(): AppSettings

    fun save(settings: AppSettings)
}

class SharedPreferencesSettingsStore @Inject constructor(@ApplicationContext context: Context) : SettingsStore {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun load() = AppSettings.parse(preferences.getString(THEME, null), preferences.getString(MAP_STYLE, null))

    override fun save(settings: AppSettings) = preferences.edit {
        putString(THEME, settings.theme.name)
        putString(MAP_STYLE, settings.mapStyle.name)
    }

    private companion object {
        const val THEME = "theme"
        const val MAP_STYLE = "mapStyle"
    }
}

/** The settings of this phone. */
@Singleton
class Settings @Inject constructor(private val store: SettingsStore) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setTheme(theme: ThemeMode) = change { it.copy(theme = theme) }

    fun setMapStyle(mapStyle: MapStyle) = change { it.copy(mapStyle = mapStyle) }

    private fun change(transform: (AppSettings) -> AppSettings) {
        _state.update(transform)
        store.save(_state.value)
    }
}
