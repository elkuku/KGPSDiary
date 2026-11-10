package org.elkuku.kgpsdiary.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.asString

/** Creates a waypoint, or edits one; closes itself once saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWaypointScreen(onDone: () -> Unit, viewModel: EditWaypointViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.id == null) R.string.waypoint_new else R.string.waypoint_edit)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = { viewModel.save(context) }, enabled = state.form.isValid) {
                            Icon(painterResource(R.drawable.ic_check), stringResource(R.string.save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        WaypointFormFields(
            state = state,
            onChange = viewModel::update,
            modifier = Modifier.padding(padding).imePadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WaypointFormFields(
    state: EditWaypointUiState,
    onChange: ((WaypointForm) -> WaypointForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    var pickDate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value) } },
            label = { Text(stringResource(R.string.waypoint_title)) },
            isError = form.title.isBlank(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { value -> onChange { it.copy(description = value) } },
            label = { Text(stringResource(R.string.waypoint_description)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.latitude,
                onValueChange = { value -> onChange { it.copy(latitude = value) } },
                label = { Text(stringResource(R.string.waypoint_latitude)) },
                isError = form.latitudeValue == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.longitude,
                onValueChange = { value -> onChange { it.copy(longitude = value) } },
                label = { Text(stringResource(R.string.waypoint_longitude)) },
                isError = form.longitudeValue == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }) {
                Text(
                    form.visitedOn?.let { stringResource(R.string.waypoint_visited_on, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) }
                        ?: stringResource(R.string.waypoint_no_visit_date),
                )
            }
            if (form.visitedOn != null) {
                TextButton(onClick = { onChange { it.copy(visitedOn = null) } }) { Text(stringResource(R.string.waypoint_clear_date)) }
            }
        }

        CategoryField(state, onChange)

        if (state.tags.isNotEmpty()) {
            Text(stringResource(R.string.waypoint_tags), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag ->
                    val selected = tag.id in form.tagIds
                    FilterChip(
                        selected = selected,
                        onClick = { onChange { it.copy(tagIds = if (selected) it.tagIds - tag.id else it.tagIds + tag.id) } },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
    }

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (form.visitedOn ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange { it.copy(visitedOn = day) }
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(state: EditWaypointUiState, onChange: ((WaypointForm) -> WaypointForm) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.categories.find { it.id == state.form.categoryId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: stringResource(R.string.waypoint_no_category),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.waypoint_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.waypoint_no_category)) },
                onClick = {
                    onChange { it.copy(categoryId = null) }
                    expanded = false
                },
            )
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onChange { it.copy(categoryId = category.id) }
                        expanded = false
                    },
                )
            }
        }
    }
}
