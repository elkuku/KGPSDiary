package org.elkuku.kgpsdiary.ui.edit

import androidx.compose.foundation.layout.Arrangement
import org.elkuku.kgpsdiary.ui.photo.serverUrl
import org.elkuku.kgpsdiary.ui.photo.PhotoViewer
import org.elkuku.kgpsdiary.ui.photo.PhotoRow
import org.elkuku.kgpsdiary.ui.photo.PhotoItem
import org.elkuku.kgpsdiary.data.Photo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.data.CategoryInput
import org.elkuku.kgpsdiary.data.TagInput
import org.elkuku.kgpsdiary.ui.manage.CategoryDialog
import org.elkuku.kgpsdiary.ui.manage.CategoryBadge
import org.elkuku.kgpsdiary.ui.manage.TagDialog
import org.elkuku.kgpsdiary.asString

/** Creates a waypoint, or edits one; closes itself once saved. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        Column(Modifier.padding(padding).imePadding()) {
            // While typing, the keyboard needs the room
            if (!WindowInsets.isImeVisible) {
                PositionMap(
                    latitude = state.form.latitudeValue,
                    longitude = state.form.longitudeValue,
                    onMove = viewModel::moveTo,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
                Text(
                    stringResource(R.string.waypoint_position_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            WaypointFormFields(
                state = state,
                onChange = viewModel::update,
                onCreateCategory = { input, onCreated -> viewModel.createCategory(context, input, onCreated) },
                onCreateTag = { input, onCreated -> viewModel.createTag(context, input, onCreated) },
                photos = {
                    PhotosField(
                        state = state,
                        onPick = viewModel::addPhotos,
                        onRemovePending = viewModel::removePendingPhoto,
                        onDelete = { photoId -> viewModel.deletePhoto(context, photoId) },
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WaypointFormFields(
    state: EditWaypointUiState,
    onChange: ((WaypointForm) -> WaypointForm) -> Unit,
    onCreateCategory: (CategoryInput, onCreated: () -> Unit) -> Unit,
    onCreateTag: (TagInput, onCreated: () -> Unit) -> Unit,
    photos: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    var pickDate by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }

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

        CategoryField(state, onChange, onNew = { newCategory = true })

        photos()

        Text(stringResource(R.string.waypoint_tags), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.tags.forEach { tag ->
                FilterChip(
                    selected = tag.id in form.tagIds,
                    onClick = { onChange { it.toggleTag(tag.id) } },
                    label = { Text(tag.name) },
                )
            }
            AssistChip(
                onClick = { newTag = true },
                label = { Text(stringResource(R.string.manage_new_tag)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }

    if (newCategory) {
        CategoryDialog(
            category = null,
            busy = state.creating,
            onDismiss = { newCategory = false },
            onSave = { input -> onCreateCategory(input) { newCategory = false } },
        )
    }
    if (newTag) {
        TagDialog(
            tag = null,
            busy = state.creating,
            onDismiss = { newTag = false },
            onSave = { input -> onCreateTag(input) { newTag = false } },
        )
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

/** The waypoint's photos, and photos picked for the upload; adding opens the photo picker. */
@Composable
private fun PhotosField(
    state: EditWaypointUiState,
    onPick: (List<Uri>) -> Unit,
    onRemovePending: (Uri) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) { uris ->
        if (uris.isNotEmpty()) onPick(uris)
    }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<Photo?>(null) }

    Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleSmall)
    val items = state.photos.map { photo ->
        PhotoItem("p${photo.id}", serverUrl(photo.thumbnailUrl), onDelete = { confirmDelete = photo.id }, onOpen = { viewing = photo })
    } + state.pendingPhotos.map { uri -> PhotoItem("u$uri", uri, onDelete = { onRemovePending(uri) }) }
    if (items.isNotEmpty()) PhotoRow(items)
    if (state.pendingPhotos.isNotEmpty()) {
        Text(
            pluralStringResource(R.plurals.photos_pending, state.pendingPhotos.size, state.pendingPhotos.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
        Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null)
        Text(stringResource(R.string.photo_add), Modifier.padding(start = 8.dp))
    }

    viewing?.let { PhotoViewer(it) { viewing = null } }
    confirmDelete?.let { photoId ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.photo_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(photoId)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val MAX_PICKED = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(state: EditWaypointUiState, onChange: ((WaypointForm) -> WaypointForm) -> Unit, onNew: () -> Unit) {
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
                    leadingIcon = { CategoryBadge(category.color, category.icon, 22) },
                    onClick = {
                        onChange { it.copy(categoryId = category.id) }
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_new_category)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = {
                    expanded = false
                    onNew()
                },
            )
        }
    }
}
