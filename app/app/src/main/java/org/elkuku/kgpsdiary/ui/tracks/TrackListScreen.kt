package org.elkuku.kgpsdiary.ui.tracks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.asString
import org.elkuku.kgpsdiary.track.GpxShare

/** The recorded tracks, newest first; a tap shows what can be done with one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(onShowOnMap: (Int) -> Unit, onBack: () -> Unit, viewModel: TrackListViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.tracks.isEmpty()) {
                Text(
                    stringResource(R.string.tracks_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.tracks, key = { it.id }) { track ->
                    val visible = track.id !in state.hidden
                    ListItem(
                        headlineContent = { Text(track.name) },
                        supportingContent = { Text(trackDetails(track)) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_timeline), contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.toggleVisible(track.id) }) {
                                Icon(
                                    painterResource(if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                    contentDescription = stringResource(if (visible) R.string.track_hide else R.string.track_show),
                                )
                            }
                        },
                        // Hidden ones dimmed, as they are not on the map
                        modifier = Modifier.clickable { viewModel.select(track.id) }.alpha(if (visible) 1f else 0.5f),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    val exportTitle = stringResource(R.string.track_export)
    state.selected?.let { track ->
        TrackSheet(
            track = track,
            busy = state.busy,
            onDismiss = { viewModel.select(null) },
            onShowOnMap = {
                viewModel.select(null)
                onShowOnMap(track.id)
            },
            onRename = { name -> viewModel.rename(context, track.id, name) },
            onExport = { context.startActivity(GpxShare.intent(context, track, exportTitle)) },
            onDelete = { viewModel.delete(context, track.id) },
        )
    }
}
