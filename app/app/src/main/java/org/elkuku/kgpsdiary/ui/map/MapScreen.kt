package org.elkuku.kgpsdiary.ui.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import org.elkuku.kgpsdiary.ui.manage.ColorDot
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotationGroup
import com.mapbox.maps.extension.compose.annotation.generated.rememberCircleAnnotationGroupInteractionsState
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.track.GpxShare
import org.elkuku.kgpsdiary.ui.tracks.TrackListViewModel
import org.elkuku.kgpsdiary.ui.tracks.TrackSheet
import org.elkuku.kgpsdiary.asString
import org.elkuku.kgpsdiary.data.Category
import org.elkuku.kgpsdiary.data.GeoLink
import org.elkuku.kgpsdiary.ui.photo.serverUrl
import org.elkuku.kgpsdiary.ui.photo.PhotoViewer
import org.elkuku.kgpsdiary.ui.photo.PhotoRow
import org.elkuku.kgpsdiary.ui.photo.PhotoItem
import org.elkuku.kgpsdiary.data.Photo
import org.elkuku.kgpsdiary.data.Waypoint

/**
 * The map of the user's waypoints: a long press adds one there, the button one at the map's
 * center; a tap on a marker shows the waypoint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    /** A waypoint to fly to and show, e.g. chosen in the list; [onFocused] once shown. */
    focusId: Int?,
    /** A track to show whole, chosen in the track list. */
    focusTrackId: Int?,
    onTracks: () -> Unit,
    onFocused: () -> Unit,
    onList: () -> Unit,
    onAdd: (latitude: Double, longitude: Double) -> Unit,
    onEdit: (id: Int) -> Unit,
    onProfile: () -> Unit,
    onManage: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    // A tap on a track shows the same actions as in the track list
    trackActions: TrackListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trackState by trackActions.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    val trackMessage = trackState.message?.asString()
    val viewportState = rememberMapViewportState {
        setCameraOptions { center(Point.fromLngLat(0.0, 20.0)).zoom(1.5) }
    }
    var fitted by rememberSaveable { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        locationGranted = grants.values.any { it }
        if (locationGranted) viewportState.transitionToFollowPuckState()
    }

    // Recording needs the precise location, and from Android 13 a notification for the service
    val recordingPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationGranted = true
            viewModel.startRecording()
            viewportState.transitionToFollowPuckState()
        }
    }

    LaunchedEffect(Unit) { viewModel.load(context) }
    // Where the map shows a waypoint or track chosen in a list; applied once the map view exists
    var focusPoints by remember { mutableStateOf<List<Point>?>(null) }
    LaunchedEffect(focusId, state.waypoints) {
        val waypoint = state.waypoints.find { it.id == focusId } ?: return@LaunchedEffect
        // Shown on the map: the filters let it through
        viewModel.clearFilters()
        viewModel.select(waypoint.id)
        fitted = true
        focusPoints = listOf(Point.fromLngLat(waypoint.longitude, waypoint.latitude))
        onFocused()
    }
    LaunchedEffect(focusTrackId) {
        // A hidden track asked for is shown again
        focusTrackId?.let(viewModel::showTrack)
    }
    LaunchedEffect(focusTrackId, state.tracks) {
        val track = state.tracks.find { it.id == focusTrackId } ?: return@LaunchedEffect
        fitted = true
        focusPoints = track.points.filter { it.size >= 2 }.map { Point.fromLngLat(it[1], it[0]) }
        onFocused()
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(trackMessage) {
        if (trackMessage != null) {
            snackbarHostState.showSnackbar(trackMessage)
            trackActions.dismissMessage()
        }
    }
    // Once, after the first load: show all waypoints
    LaunchedEffect(state.loaded) {
        if (state.loaded && !fitted && state.waypoints.isNotEmpty()) {
            fitted = true
            focusPoints = state.waypoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onTracks) {
                        Icon(painterResource(R.drawable.ic_timeline), stringResource(R.string.tracks_title))
                    }
                    IconButton(onClick = onList) {
                        Icon(painterResource(R.drawable.ic_list), stringResource(R.string.list_title))
                    }
                    IconButton(onClick = { viewModel.load(context) }, enabled = !state.loading) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
                    }
                    IconButton(onClick = onManage) {
                        Icon(painterResource(R.drawable.ic_label), stringResource(R.string.manage_title))
                    }
                    IconButton(onClick = onProfile) {
                        Icon(painterResource(R.drawable.ic_person), stringResource(R.string.profile))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.recording && !state.unsavedTrack) {
                    SmallFloatingActionButton(onClick = {
                        recordingPermissions.launch(
                            listOfNotNull(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
                            ).toTypedArray(),
                        )
                    }) {
                        Icon(painterResource(R.drawable.ic_record), stringResource(R.string.recording_start), tint = Color(0xFFD50000))
                    }
                }
                SmallFloatingActionButton(onClick = {
                    if (locationGranted) {
                        viewportState.transitionToFollowPuckState()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.map_my_location))
                }
                FloatingActionButton(onClick = {
                    val center = viewportState.cameraState?.center ?: return@FloatingActionButton
                    onAdd(center.latitude(), center.longitude())
                }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.map_add_here))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Mapbox's Standard style, in its night light when the phone is in dark mode
            val dark = isSystemInDarkTheme()
            val styleState = rememberStandardStyleState()
            LaunchedEffect(dark) {
                styleState.configurationsState.lightPreset = if (dark) LightPresetValue.NIGHT else LightPresetValue.DAY
            }
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = viewportState,
                style = { MapboxStandardStyle(standardStyleState = styleState) },
                onMapLongClickListener = { point ->
                    onAdd(point.latitude(), point.longitude())
                    true
                },
            ) {
                MapEffect(focusPoints) { mapView ->
                    val points = focusPoints?.takeIf { it.isNotEmpty() } ?: return@MapEffect
                    focusPoints = null
                    // Mapbox fits the points into the map's real size, once it knows it; clear of the
                    // filter chips at the top and the buttons on the right
                    val dp = mapView.resources.displayMetrics.density.toDouble()
                    mapView.mapboxMap.cameraForCoordinates(
                        points,
                        CameraOptions.Builder().build(),
                        EdgeInsets(120 * dp, 48 * dp, 64 * dp, 88 * dp),
                        FOCUS_MAX_ZOOM,
                        null,
                    ) { camera -> mapView.mapboxMap.setCamera(camera) }
                }
                MapEffect(locationGranted) { mapView ->
                    mapView.location.updateSettings {
                        enabled = locationGranted
                        locationPuck = createDefault2DPuck(withBearing = true)
                    }
                }
                TrackLines(state.tracks, state.recordedPoints, night = dark, onClick = trackActions::select)
                WaypointMarkers(state.visibleWaypoints, viewportState, onClick = viewModel::select)
            }

            if (state.recording) {
                val chips = state.categories.isNotEmpty() || state.tags.isNotEmpty()
                RecordingBar(
                    state.recordedPoints,
                    onStop = viewModel::stopRecording,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = if (chips) 80.dp else 28.dp),
                )
            }

            if (state.categories.isNotEmpty() || state.tags.isNotEmpty()) {
                // Below Mapbox's scale bar
                FilterChips(state, viewModel::filterCategory, viewModel::filterTag, Modifier.align(Alignment.TopStart).padding(top = 28.dp))
            }

            // The map's center, where the add button puts a waypoint
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(20.dp),
                tint = Color(0xAA000000),
            )

            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            }

            if (state.offline) {
                OfflineNotice(state.syncedAt, onRetry = { viewModel.load(context) }, modifier = Modifier.align(Alignment.BottomStart))
            }
        }
    }

    if (state.unsavedTrack) {
        SaveTrackDialog(
            points = state.recordedPoints,
            saving = state.savingTrack,
            onSave = { name -> viewModel.saveTrack(context, name) },
            onDiscard = viewModel::discardRecording,
        )
    }

    val exportTitle = stringResource(R.string.track_export)
    trackState.selected?.let { track ->
        TrackSheet(
            track = track,
            busy = trackState.busy,
            onDismiss = { trackActions.select(null) },
            onHide = {
                trackActions.select(null)
                trackActions.toggleVisible(track.id)
            },
            onRename = { name -> trackActions.rename(context, track.id, name) },
            onExport = { context.startActivity(GpxShare.intent(context, track, exportTitle)) },
            onDelete = { trackActions.delete(context, track.id) },
        )
    }

    state.selected?.let { waypoint ->
        WaypointSheet(
            waypoint = waypoint,
            onDismiss = { viewModel.select(null) },
            onEdit = {
                viewModel.select(null)
                onEdit(waypoint.id)
            },
            onDelete = { viewModel.delete(context, waypoint.id) },
        )
    }
}

/** Without a connection: the map shows the diary as the phone kept it. */
@Composable
private fun OfflineNotice(syncedAt: Instant?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(start = 8.dp, end = 96.dp, bottom = 40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (syncedAt == null) {
                    stringResource(R.string.offline_nothing_saved)
                } else {
                    stringResource(
                        R.string.offline_saved_at,
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(syncedAt.atZone(ZoneId.systemDefault())),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** One chip per category (with its color) and tag; a selected chip filters the map. */
@Composable
private fun FilterChips(state: MapUiState, onCategory: (Int) -> Unit, onTag: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.filtered) {
            item {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.map_shown, state.visibleWaypoints.size, state.waypoints.size)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
        items(state.categories, key = { "c${it.id}" }) { category ->
            FilterChip(
                selected = category.id == state.categoryId,
                onClick = { onCategory(category.id) },
                label = { Text(category.name) },
                leadingIcon = { ColorDot(category.color, 12) },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
        items(state.tags, key = { "t${it.id}" }) { tag ->
            FilterChip(
                selected = tag.id == state.tagId,
                onClick = { onTag(tag.id) },
                label = { Text("#${tag.name}") },
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    }
}

@Composable
private fun WaypointMarkers(waypoints: List<Waypoint>, viewportState: MapViewportState, onClick: (Int) -> Unit) {
    val options = remember(waypoints) {
        waypoints.map { waypoint ->
            CircleAnnotationOptions()
                .withPoint(Point.fromLngLat(waypoint.longitude, waypoint.latitude))
                .withCircleColor(parseColor(waypoint.category?.color))
                .withCircleRadius(9.0)
                .withData(JsonPrimitive(waypoint.id))
        }
    }

    val currentOnClick by rememberUpdatedState(onClick)
    val interactions = rememberCircleAnnotationGroupInteractionsState {
        onClicked { annotation ->
            annotation.getData()?.asInt?.let { currentOnClick(it) }
            true
        }
        onClusterClicked { cluster ->
            val point = cluster.originalFeature.geometry() as? Point
            val zoom = viewportState.cameraState?.zoom ?: 2.0
            if (point != null) {
                viewportState.easeTo(CameraOptions.Builder().center(point).zoom(zoom + 2).build())
            }
            true
        }
    }

    CircleAnnotationGroup(
        annotations = options,
        annotationConfig = AnnotationConfig(
            annotationSourceOptions = AnnotationSourceOptions(clusterOptions = ClusterOptions(clusterRadius = 50L)),
        ),
    ) {
        circleStrokeColor = Color.White
        circleStrokeWidth = 2.0
        // Unlit: the Standard style's night light would darken the category colors
        circleEmissiveStrength = 1.0
        interactionsState = interactions
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WaypointSheet(waypoint: Waypoint, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Photo?>(null) }

    viewing?.let { PhotoViewer(it) { viewing = null } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(waypoint.title, style = MaterialTheme.typography.headlineSmall)

            val details = listOfNotNull(
                waypoint.visitedOn?.let { stringResource(R.string.waypoint_visited_on, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) },
                "%.5f, %.5f".format(waypoint.latitude, waypoint.longitude),
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (waypoint.category != null || waypoint.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    waypoint.category?.let { category ->
                        AssistChip(onClick = {}, label = { Text(category.name) }, leadingIcon = {
                            ColorDot(category.color, 12)
                        })
                    }
                    waypoint.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag.name) }) }
                }
            }

            if (waypoint.description.isNotBlank()) {
                Text(waypoint.description, style = MaterialTheme.typography.bodyLarge, maxLines = 8)
            }

            if (waypoint.photos.isNotEmpty()) {
                PhotoRow(
                    waypoint.photos.map { photo ->
                        PhotoItem(key = "p${photo.id}", model = serverUrl(photo.thumbnailUrl), onOpen = { viewing = photo })
                    },
                )
            }

            // Round icon buttons, edit filled as the main action; the labels are their descriptions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilledIconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
                }
                // Only the coordinates: the title and description stay private
                val shareTitle = stringResource(R.string.waypoint_share)
                OutlinedIconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, GeoLink.shareText(waypoint.latitude, waypoint.longitude))
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = shareTitle)
                }
                // Only apps that can show a position: the geo: link opened, with the app picker each time
                val mapAppTitle = stringResource(R.string.waypoint_open_in_map_app)
                val noMapApp = stringResource(R.string.waypoint_no_map_app)
                OutlinedIconButton(onClick = {
                    val view = Intent(Intent.ACTION_VIEW, GeoLink.uri(waypoint.latitude, waypoint.longitude).toUri())
                    try {
                        context.startActivity(Intent.createChooser(view, mapAppTitle))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, noMapApp, Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_map), contentDescription = mapAppTitle)
                }
                OutlinedIconButton(
                    onClick = { confirmDelete = true },
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.waypoint_delete_title)) },
            text = { Text(stringResource(R.string.waypoint_delete_text, waypoint.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** How close the map comes when it fits points: a single waypoint at street level. */
private const val FOCUS_MAX_ZOOM = 15.0

/** `#rrggbb` as an ARGB color; the default marker color for anything else. */
internal fun parseColor(hex: String?): Int = runCatching {
    (hex ?: Category.DEFAULT_COLOR).toColorInt()
}.getOrDefault(Color(0xFFD84315).toArgb())

private fun hasLocationPermission(context: android.content.Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
