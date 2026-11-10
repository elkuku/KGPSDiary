package org.elkuku.kgpsdiary.data

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.elkuku.kgpsdiary.auth.SessionManager

/**
 * The signed-in user's diary, as last loaded from the server. Changes go to the server first and
 * then update the loaded lists.
 */
@Singleton
class DiaryRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KgpsApi,
) {
    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    fun waypoint(id: Int): Waypoint? = _waypoints.value.find { it.id == id }

    /** Loads the waypoints, categories and tags; the first failure is the result. */
    suspend fun refresh(context: Context): ApiResult<Unit> {
        val waypoints = sessionManager.authorized(context, api::waypoints)
        if (waypoints is ApiResult.Success) _waypoints.value = waypoints.value else return waypoints.withoutValue()

        val categories = sessionManager.authorized(context, api::categories)
        if (categories is ApiResult.Success) _categories.value = categories.value else return categories.withoutValue()

        val tags = sessionManager.authorized(context, api::tags)
        if (tags is ApiResult.Success) _tags.value = tags.value else return tags.withoutValue()

        return ApiResult.Success(Unit)
    }

    /** Creates a waypoint, or changes the one with [id]. */
    suspend fun save(context: Context, id: Int?, input: WaypointInput): ApiResult<Waypoint> {
        val result = sessionManager.authorized(context) { token ->
            if (id == null) api.createWaypoint(token, input) else api.updateWaypoint(token, id, input)
        }
        if (result is ApiResult.Success) {
            val saved = result.value
            _waypoints.update { list -> listOf(saved) + list.filter { it.id != saved.id } }
        }
        return result
    }

    suspend fun delete(context: Context, id: Int): ApiResult<Unit> {
        val result = sessionManager.authorized(context) { token -> api.deleteWaypoint(token, id) }
        if (result is ApiResult.Success) {
            _waypoints.update { list -> list.filter { it.id != id } }
        }
        return result
    }

    /** Forgets the diary, e.g. after signing out. */
    fun clear() {
        _waypoints.value = emptyList()
        _categories.value = emptyList()
        _tags.value = emptyList()
    }

    private fun ApiResult<*>.withoutValue(): ApiResult<Unit> = when (this) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Unauthorized -> this
        is ApiResult.Failure -> this
    }
}
