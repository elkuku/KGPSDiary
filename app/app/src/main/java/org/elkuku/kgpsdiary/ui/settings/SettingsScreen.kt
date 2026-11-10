package org.elkuku.kgpsdiary.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.settings.MapStyle
import org.elkuku.kgpsdiary.settings.Settings
import org.elkuku.kgpsdiary.settings.ThemeMode

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: Settings) : ViewModel() {
    val state = settings.state

    fun setTheme(theme: ThemeMode) = settings.setTheme(theme)

    fun setMapStyle(mapStyle: MapStyle) = settings.setMapStyle(mapStyle)
}

/** The settings of this phone: the theme and the map style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.settings_theme))
            Choices(
                options = ThemeMode.entries,
                selected = state.theme,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                        ThemeMode.LIGHT -> R.string.settings_theme_light
                        ThemeMode.DARK -> R.string.settings_theme_dark
                    }
                },
                onSelect = viewModel::setTheme,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(stringResource(R.string.settings_map_style))
            Choices(
                options = MapStyle.entries,
                selected = state.mapStyle,
                label = {
                    when (it) {
                        MapStyle.STANDARD -> R.string.settings_map_standard
                        MapStyle.STANDARD_WITHOUT_BUSINESSES -> R.string.settings_map_without_businesses
                    }
                },
                onSelect = viewModel::setMapStyle,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** One radio button per option. */
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The row handles the click
                RadioButton(selected = option == selected, onClick = null)
                Text(stringResource(label(option)), Modifier.padding(start = 16.dp))
            }
        }
    }
}
