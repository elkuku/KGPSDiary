package org.elkuku.kgpsdiary.ui.list

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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.data.DiaryRepository
import org.elkuku.kgpsdiary.data.Waypoint
import org.elkuku.kgpsdiary.data.searchWaypoints
import org.elkuku.kgpsdiary.ui.manage.ColorDot

data class WaypointListUiState(val query: String = "", val all: Int = 0, val found: List<Waypoint> = emptyList())

@HiltViewModel
class WaypointListViewModel @Inject constructor(diary: DiaryRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<WaypointListUiState> = combine(query, diary.waypoints) { query, waypoints ->
        WaypointListUiState(query, waypoints.size, searchWaypoints(waypoints, query))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaypointListUiState())

    fun search(text: String) {
        query.value = text
    }
}

/** All waypoints, newest visit first, with a search; a tap shows the waypoint on the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointListScreen(onOpen: (Int) -> Unit, onBack: () -> Unit, viewModel: WaypointListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                placeholder = { Text(stringResource(R.string.list_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.list_clear))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                pluralStringResource(R.plurals.list_found, state.found.size, state.found.size, state.all),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.found, key = { it.id }) { waypoint ->
                    WaypointRow(waypoint, Modifier.clickable { onOpen(waypoint.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WaypointRow(waypoint: Waypoint, modifier: Modifier = Modifier) {
    val details = listOfNotNull(
        waypoint.visitedOn?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        waypoint.category?.name,
        waypoint.tags.joinToString(" ") { "#${it.name}" }.ifEmpty { null },
    ).joinToString(" · ")

    ListItem(
        headlineContent = { Text(waypoint.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (details.isNotEmpty()) Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (waypoint.description.isNotBlank()) {
                    Text(waypoint.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = { ColorDot(waypoint.category?.color ?: org.elkuku.kgpsdiary.data.Category.DEFAULT_COLOR, 16) },
        modifier = modifier,
    )
}
