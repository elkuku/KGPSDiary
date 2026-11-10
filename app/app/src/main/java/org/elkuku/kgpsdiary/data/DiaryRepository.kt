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

    /** Creates a category, or changes the one with [id]; the waypoints show the change. */
    suspend fun saveCategory(context: Context, id: Int?, input: CategoryInput): ApiResult<Category> {
        val result = sessionManager.authorized(context) { token -> api.saveCategory(token, id, input) }
        if (result is ApiResult.Success) {
            val saved = result.value
            _categories.update { list -> (list.filter { it.id != saved.id } + saved).sortedBy { it.name.lowercase() } }
            _waypoints.update { list -> list.map { if (it.category?.id == saved.id) it.copy(category = saved) else it } }
        }
        return result
    }

    /** Deletes a category; its waypoints stay, without a category. */
    suspend fun deleteCategory(context: Context, id: Int): ApiResult<Unit> {
        val result = sessionManager.authorized(context) { token -> api.deleteCategory(token, id) }
        if (result is ApiResult.Success) {
            _categories.update { list -> list.filter { it.id != id } }
            _waypoints.update { list -> list.map { if (it.category?.id == id) it.copy(category = null) else it } }
        }
        return result
    }

    /** Creates a tag, or renames the one with [id]. */
    suspend fun saveTag(context: Context, id: Int?, input: TagInput): ApiResult<Tag> {
        val result = sessionManager.authorized(context) { token -> api.saveTag(token, id, input) }
        if (result is ApiResult.Success) {
            val saved = result.value
            _tags.update { list -> (list.filter { it.id != saved.id } + saved).sortedBy { it.name.lowercase() } }
            _waypoints.update { list ->
                list.map { waypoint -> waypoint.copy(tags = waypoint.tags.map { if (it.id == saved.id) saved else it }) }
            }
        }
        return result
    }

    /** Deletes a tag; its waypoints stay. */
    suspend fun deleteTag(context: Context, id: Int): ApiResult<Unit> {
        val result = sessionManager.authorized(context) { token -> api.deleteTag(token, id) }
        if (result is ApiResult.Success) {
            _tags.update { list -> list.filter { it.id != id } }
            _waypoints.update { list -> list.map { waypoint -> waypoint.copy(tags = waypoint.tags.filter { it.id != id }) } }
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
