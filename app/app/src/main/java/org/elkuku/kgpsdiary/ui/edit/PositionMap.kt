package org.elkuku.kgpsdiary.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.ui.settings.SettingsViewModel
import org.elkuku.kgpsdiary.ui.theme.LocalDarkTheme

private val PinColor = Color(0xFFD50000)

/**
 * A map with a pin in its middle: the waypoint is where the pin points, moving the map moves it.
 * Starts at [latitude], [longitude]; [onMove] gets the pin's position after each move.
 */
@Composable
fun PositionMap(
    latitude: Double?,
    longitude: Double?,
    onMove: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
    settings: SettingsViewModel = hiltViewModel(),
) {
    val appSettings by settings.state.collectAsStateWithLifecycle()
    val currentOnMove by rememberUpdatedState(onMove)
    val viewportState = rememberMapViewportState {
        val known = latitude != null && longitude != null
        setCameraOptions {
            center(Point.fromLngLat(longitude ?: 0.0, latitude ?: 20.0))
            zoom(if (known) START_ZOOM else 1.5)
        }
    }

    LaunchedEffect(viewportState) {
        snapshotFlow { viewportState.cameraState?.center }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { center -> currentOnMove(center.latitude(), center.longitude()) }
    }

    Box(modifier) {
        val dark = LocalDarkTheme.current
        val styleState = rememberStandardStyleState()
        LaunchedEffect(dark, appSettings.mapStyle) {
            styleState.configurationsState.lightPreset = if (dark) LightPresetValue.NIGHT else LightPresetValue.DAY
            styleState.configurationsState.showPointOfInterestLabels = BooleanValue(appSettings.mapStyle.showPointsOfInterest)
        }
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = viewportState,
            style = { MapboxStandardStyle(standardStyleState = styleState) },
            // Small: only the logo and attribution, which Mapbox requires
            scaleBar = {},
            compass = {},
        )
        // The pin's tip in the middle
        Icon(
            painterResource(R.drawable.ic_place),
            contentDescription = stringResource(R.string.waypoint_position),
            tint = PinColor,
            modifier = Modifier.align(Alignment.Center).offset(y = (-PIN_SIZE / 2).dp).size(PIN_SIZE.dp),
        )
    }
}

private const val START_ZOOM = 16.0
private const val PIN_SIZE = 40
