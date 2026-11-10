package org.elkuku.kgpsdiary.ui.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.data.Track

/** Date, distance, duration and points, e.g. "Nov 10, 2026, 10:16 PM · 2.39 km · 2 min · 37 points". */
@Composable
fun trackDetails(track: Track): String {
    val summary = TrackSummary.of(track)
    val minutes = summary.duration.toMinutes()
    val locale = LocalConfiguration.current.locales[0]
    return listOfNotNull(
        summary.startedAt?.atZoneSameInstant(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
        String.format(locale, "%.2f km", summary.kilometers),
        if (minutes >= 60) stringResource(R.string.track_duration_hours, minutes / 60, minutes % 60) else stringResource(R.string.track_duration_minutes, minutes),
        pluralStringResource(R.plurals.track_points, track.pointCount, track.pointCount),
    ).joinToString(" · ")
}

/**
 * What can be done with a track: [onShowOnMap] fits the map to it, [onHide] (on the map) takes it off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    track: Track,
    busy: Boolean,
    onDismiss: () -> Unit,
    onShowOnMap: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(track.name, style = MaterialTheme.typography.headlineSmall)
            Text(trackDetails(track), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                onShowOnMap?.let {
                    FilledIconButton(onClick = it) {
                        Icon(painterResource(R.drawable.ic_map), contentDescription = stringResource(R.string.waypoint_show_on_map))
                    }
                }
                onHide?.let {
                    OutlinedIconButton(onClick = it) {
                        Icon(painterResource(R.drawable.ic_visibility_off), contentDescription = stringResource(R.string.track_hide))
                    }
                }
                OutlinedIconButton(onClick = { renaming = true }, enabled = !busy) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.track_rename))
                }
                OutlinedIconButton(onClick = onExport) {
                    Icon(painterResource(R.drawable.ic_file_download), contentDescription = stringResource(R.string.track_export))
                }
                OutlinedIconButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy,
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(track.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.track_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.track_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    onRename(name)
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.track_delete_text, track.name)) },
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
