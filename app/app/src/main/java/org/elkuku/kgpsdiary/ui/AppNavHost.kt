package org.elkuku.kgpsdiary.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.elkuku.kgpsdiary.ui.edit.EditWaypointScreen
import org.elkuku.kgpsdiary.ui.list.WaypointListScreen
import org.elkuku.kgpsdiary.ui.manage.ManageScreen
import org.elkuku.kgpsdiary.ui.map.MapScreen

@Serializable
object MapRoute

/** A new waypoint at [latitude], [longitude] (both set), or the waypoint with [id]. */
@Serializable
data class EditWaypointRoute(val id: Int? = null, val latitude: Double? = null, val longitude: Double? = null)

@Serializable
object ProfileRoute

@Serializable
object ManageRoute

@Serializable
object ListRoute

/** The saved-state key the list hands the map: the waypoint to show. */
private const val FOCUS = "focus"

/** The screens of a signed-in user. */
@Composable
fun AppNavHost(
    mainState: MainUiState,
    onRefreshProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MapRoute, modifier = modifier) {
        composable<MapRoute> { entry ->
            val focus by entry.savedStateHandle.getStateFlow<Int?>(FOCUS, null).collectAsStateWithLifecycle()
            MapScreen(
                focusId = focus,
                onFocused = { entry.savedStateHandle[FOCUS] = null },
                onList = { navController.navigate(ListRoute) },
                onAdd = { latitude, longitude -> navController.navigate(EditWaypointRoute(latitude = latitude, longitude = longitude)) },
                onEdit = { id -> navController.navigate(EditWaypointRoute(id = id)) },
                onProfile = { navController.navigate(ProfileRoute) },
                onManage = { navController.navigate(ManageRoute) },
            )
        }
        composable<ListRoute> {
            WaypointListScreen(
                onOpen = { id ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(FOCUS, id)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ManageRoute> {
            ManageScreen(onBack = { navController.popBackStack() })
        }
        composable<EditWaypointRoute> {
            EditWaypointScreen(onDone = { navController.popBackStack() })
        }
        composable<ProfileRoute> {
            ProfileScreen(
                state = mainState,
                onRefresh = onRefreshProfile,
                onSignOut = onSignOut,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
