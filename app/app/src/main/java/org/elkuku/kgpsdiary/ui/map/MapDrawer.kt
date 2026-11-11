package org.elkuku.kgpsdiary.ui.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.elkuku.kgpsdiary.BuildConfig
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.ui.manage.ColorDot

/** The map's menu: where to go, what to do, and the filters for the map. */
@Composable
fun MapDrawer(
    state: MapUiState,
    onTracks: () -> Unit,
    onList: () -> Unit,
    onRefresh: () -> Unit,
    onManage: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onCategory: (Int) -> Unit,
    onTag: (Int) -> Unit,
    onClearFilters: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp),
            )
            Text(
                stringResource(if (BuildConfig.DEBUG) R.string.drawer_version_debug else R.string.drawer_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 2.dp, bottom = 16.dp),
            )
            DrawerItem(R.drawable.ic_list, R.string.list_title, onList)
            DrawerItem(R.drawable.ic_timeline, R.string.tracks_title, onTracks)
            DrawerItem(R.drawable.ic_label, R.string.manage_title, onManage)
            DrawerItem(R.drawable.ic_refresh, R.string.refresh, onRefresh, enabled = !state.loading)
            DrawerItem(R.drawable.ic_settings, R.string.settings_title, onSettings)
            DrawerItem(R.drawable.ic_person, R.string.profile, onProfile)

            if (state.categories.isNotEmpty() || state.tags.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (state.filtered) {
                            stringResource(R.string.filter_title_shown, state.visibleWaypoints.size, state.waypoints.size)
                        } else {
                            stringResource(R.string.filter_title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearFilters, enabled = state.filtered) { Text(stringResource(R.string.filter_clear)) }
                }
                FlowRow(
                    Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = category.id == state.categoryId,
                            onClick = { onCategory(category.id) },
                            label = { Text(category.name) },
                            leadingIcon = { ColorDot(category.color, 12) },
                        )
                    }
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id == state.tagId,
                            onClick = { onTag(tag.id) },
                            label = { Text("#${tag.name}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(@DrawableRes icon: Int, label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        selected = false,
        onClick = { if (enabled) onClick() },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
