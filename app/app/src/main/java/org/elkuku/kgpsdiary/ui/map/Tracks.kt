package org.elkuku.kgpsdiary.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMapComposable
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.data.Track
import org.elkuku.kgpsdiary.track.RecordedPoint
import org.elkuku.kgpsdiary.track.TrackMath

private val TrackColor = Color(0xFF6A1B9A)
private val RecordingColor = Color(0xFFD50000)

/** Lighter on the night map, where the dark ones disappear. */
private val NightTrackColor = Color(0xFFCE93D8)
private val NightRecordingColor = Color(0xFFFF5252)

/** The saved tracks (purple, as on the web) and the one being recorded (red), as lines. */
@Composable
@MapboxMapComposable
fun TrackLines(tracks: List<Track>, recorded: List<RecordedPoint>, night: Boolean) {
    tracks.forEach { track ->
        val points = track.points.mapNotNull { point ->
            if (point.size >= 2) Point.fromLngLat(point[1], point[0]) else null
        }
        if (points.size >= 2) {
            PolylineAnnotation(points = points) {
                lineColor = if (night) NightTrackColor else TrackColor
                lineWidth = 4.0
                lineOpacity = 0.8
                // Unlit: the Standard style's night light would darken it
                lineEmissiveStrength = 1.0
            }
        }
    }

    if (recorded.size >= 2) {
        PolylineAnnotation(points = recorded.map { Point.fromLngLat(it.longitude, it.latitude) }) {
            lineColor = if (night) NightRecordingColor else RecordingColor
            lineWidth = 5.0
            lineEmissiveStrength = 1.0
        }
    }
}

/** While recording: the points and the distance so far, and the stop button. */
@Composable
fun RecordingBar(points: List<RecordedPoint>, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(RecordingColor))
            Text(
                pluralStringResource(R.plurals.recording_status, points.size, kilometers(TrackMath.distance(points)), points.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onStop) { Text(stringResource(R.string.recording_stop)) }
        }
    }
}

/** A stopped recording: upload it with a name, or discard it. */
@Composable
fun SaveTrackDialog(points: List<RecordedPoint>, saving: Boolean, onSave: (String) -> Unit, onDiscard: () -> Unit) {
    val defaultName = stringResource(
        R.string.track_default_name,
        LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)),
    )
    var name by remember { mutableStateOf(defaultName) }
    var confirmDiscard by remember { mutableStateOf(false) }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.track_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text(stringResource(R.string.track_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    AlertDialog(
        // Only the buttons close it: a recording is not lost by a tap beside the dialog
        onDismissRequest = {},
        title = { Text(stringResource(R.string.track_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pluralStringResource(R.plurals.recording_status, points.size, kilometers(TrackMath.distance(points)), points.size))
                if (points.size < 2) Text(stringResource(R.string.track_too_short), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.track_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, enabled = !saving && points.size >= 2 && name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = { confirmDiscard = true }, enabled = !saving) { Text(stringResource(R.string.track_discard)) } },
    )
}

private fun kilometers(meters: Double): String = String.format(Locale.getDefault(), "%.2f", meters / 1000)
