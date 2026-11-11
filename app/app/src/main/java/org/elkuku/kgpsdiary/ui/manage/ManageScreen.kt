package org.elkuku.kgpsdiary.ui.manage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.asString
import org.elkuku.kgpsdiary.data.Category
import org.elkuku.kgpsdiary.data.CategoryInput
import org.elkuku.kgpsdiary.data.Tag
import org.elkuku.kgpsdiary.data.TagInput
import org.elkuku.kgpsdiary.ui.map.parseColor

/** Marker colors to choose from: Material 800 tones that read well on the map. */
val CategoryColors = listOf(
    "#c62828", "#d84315", "#ef6c00", "#f9a825", "#2e7d32", "#00695c",
    "#00838f", "#1565c0", "#283593", "#6a1b9a", "#ad1457", "#4e342e",
)

/** What is being edited in a dialog. */
private sealed interface Editing {
    data class CategoryEdit(val category: Category?) : Editing
    data class TagEdit(val tag: Tag?) : Editing
}

/** The user's categories and tags: add, rename, recolor, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(onBack: () -> Unit, viewModel: ManageViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item { SectionHeader(stringResource(R.string.manage_categories)) { editing = Editing.CategoryEdit(null) } }
            if (state.categories.isEmpty()) item { EmptyHint(stringResource(R.string.manage_no_categories)) }
            items(state.categories, key = { "c${it.id}" }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text(waypointCount(state.categoryCounts[category.id] ?: 0)) },
                    leadingContent = { CategoryBadge(category.color, category.icon, 28) },
                    trailingContent = {
                        IconButton(onClick = { deleting = Editing.CategoryEdit(category) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete)) }
                    },
                    modifier = Modifier.clickable { editing = Editing.CategoryEdit(category) },
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { SectionHeader(stringResource(R.string.manage_tags)) { editing = Editing.TagEdit(null) } }
            if (state.tags.isEmpty()) item { EmptyHint(stringResource(R.string.manage_no_tags)) }
            items(state.tags, key = { "t${it.id}" }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    supportingContent = { Text(waypointCount(state.tagCounts[tag.id] ?: 0)) },
                    trailingContent = {
                        IconButton(onClick = { deleting = Editing.TagEdit(tag) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete)) }
                    },
                    modifier = Modifier.clickable { editing = Editing.TagEdit(tag) },
                )
            }
        }
    }

    when (val current = editing) {
        is Editing.CategoryEdit -> CategoryDialog(
            category = current.category,
            busy = state.busy,
            onDismiss = { editing = null },
            onSave = { input -> viewModel.saveCategory(context, current.category?.id, input) { editing = null } },
        )
        is Editing.TagEdit -> TagDialog(
            tag = current.tag,
            busy = state.busy,
            onDismiss = { editing = null },
            onSave = { input -> viewModel.saveTag(context, current.tag?.id, input) { editing = null } },
        )
        null -> Unit
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = {
                Text(
                    when (target) {
                        is Editing.CategoryEdit -> stringResource(R.string.manage_delete_category, target.category?.name.orEmpty())
                        is Editing.TagEdit -> stringResource(R.string.manage_delete_tag, target.tag?.name.orEmpty())
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    when (target) {
                        is Editing.CategoryEdit -> target.category?.let { viewModel.deleteCategory(context, it.id) }
                        is Editing.TagEdit -> target.tag?.let { viewModel.deleteTag(context, it.id) }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.manage_add, title)) }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun waypointCount(count: Int): String = pluralStringResource(R.plurals.waypoint_count, count, count)

@Composable
fun ColorDot(color: String, size: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) { drawCircle(Color(parseColor(color))) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(category: Category?, busy: Boolean, onDismiss: () -> Unit, onSave: (CategoryInput) -> Unit) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var color by remember { mutableStateOf(category?.color ?: CategoryColors[4]) }
    // A name the app doesn't know (typed on the web before the symbols) starts as none
    var icon by remember { mutableStateOf(category?.icon?.takeIf { CategorySymbols.drawable(it) != null }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.manage_new_category else R.string.manage_edit_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.manage_name)) },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (CategoryColors + listOfNotNull(color.takeIf { it !in CategoryColors })).forEach { option ->
                        val selected = option.equals(color, ignoreCase = true)
                        ColorDot(
                            option,
                            32,
                            Modifier
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = option },
                        )
                    }
                }
                Text(stringResource(R.string.manage_symbol), style = MaterialTheme.typography.titleSmall)
                SymbolPicker(color = color, selected = icon, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(CategoryInput(name.trim(), color, icon)) },
                enabled = !busy && name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** No symbol, then one badge per symbol in the category's [color]; the selected one framed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolPicker(color: String, selected: String?, onSelect: (String?) -> Unit) {
    val frame = MaterialTheme.colorScheme.onSurface
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<String?>(null) + CategorySymbols.all.map { it.first }).forEach { name ->
            val isSelected = name == selected
            val label = name ?: stringResource(R.string.manage_symbol_none)
            CategoryBadge(
                color,
                name,
                34,
                Modifier
                    .border(if (isSelected) 3.dp else 0.dp, if (isSelected) frame else Color.Transparent, CircleShape)
                    .semantics { contentDescription = label; this.selected = isSelected }
                    .clickable { onSelect(name) },
            )
        }
    }
}

@Composable
fun TagDialog(tag: Tag?, busy: Boolean, onDismiss: () -> Unit, onSave: (TagInput) -> Unit) {
    var name by remember { mutableStateOf(tag?.name.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (tag == null) R.string.manage_new_tag else R.string.manage_edit_tag)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.manage_name)) }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(TagInput(name.trim())) }, enabled = !busy && name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
