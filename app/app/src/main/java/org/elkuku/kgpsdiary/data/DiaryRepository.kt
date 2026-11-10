package org.elkuku.kgpsdiary.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.auth.SessionManager
import org.elkuku.kgpsdiary.data.cache.CachedDiary
import org.elkuku.kgpsdiary.data.cache.DiaryCache
import org.elkuku.kgpsdiary.uiText

/**
 * The signed-in user's diary. It starts with what the phone kept from the last time ([restore]),
 * then the server's ([refresh]). Changes go to the server first, then into the lists and the
 * phone's cache.
 */
@Singleton
class DiaryRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KgpsApi,
    private val cache: DiaryCache,
) {
    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    /** When the lists last matched the server; null before the first load. */
    private val _syncedAt = MutableStateFlow<Instant?>(null)
    val syncedAt: StateFlow<Instant?> = _syncedAt.asStateFlow()

    private val owner: String? get() = sessionManager.account.value?.email

    fun waypoint(id: Int): Waypoint? = _waypoints.value.find { it.id == id }

    /** Shows what the phone kept of the signed-in user's diary, until the server answers. */
    suspend fun restore() {
        val owner = owner ?: return
        if (_syncedAt.value != null) return

        val cached = cache.load(owner) ?: return
        _waypoints.value = cached.waypoints
        _categories.value = cached.categories
        _tags.value = cached.tags
        _syncedAt.value = cached.savedAt
    }

    /** Loads the waypoints, categories and tags; the lists change only when all three arrived. */
    suspend fun refresh(context: Context): ApiResult<Unit> {
        val waypoints = sessionManager.authorized(context, api::waypoints)
        if (waypoints !is ApiResult.Success) return waypoints.withoutValue()

        val categories = sessionManager.authorized(context, api::categories)
        if (categories !is ApiResult.Success) return categories.withoutValue()

        val tags = sessionManager.authorized(context, api::tags)
        if (tags !is ApiResult.Success) return tags.withoutValue()

        _waypoints.value = waypoints.value
        _categories.value = categories.value
        _tags.value = tags.value
        persist()

        return ApiResult.Success(Unit)
    }

    /** Creates a waypoint, or changes the one with [id]. */
    suspend fun save(context: Context, id: Int?, input: WaypointInput): ApiResult<Waypoint> =
        sessionManager.authorized(context) { token ->
            if (id == null) api.createWaypoint(token, input) else api.updateWaypoint(token, id, input)
        }.onSuccess { saved ->
            _waypoints.update { list -> listOf(saved) + list.filter { it.id != saved.id } }
        }

    suspend fun delete(context: Context, id: Int): ApiResult<Unit> =
        sessionManager.authorized(context) { token -> api.deleteWaypoint(token, id) }.onSuccess {
            _waypoints.update { list -> list.filter { it.id != id } }
        }

    /** Creates a category, or changes the one with [id]; the waypoints show the change. */
    suspend fun saveCategory(context: Context, id: Int?, input: CategoryInput): ApiResult<Category> =
        sessionManager.authorized(context) { token -> api.saveCategory(token, id, input) }.onSuccess { saved ->
            _categories.update { list -> (list.filter { it.id != saved.id } + saved).sortedBy { it.name.lowercase() } }
            _waypoints.update { list -> list.map { if (it.category?.id == saved.id) it.copy(category = saved) else it } }
        }

    /** Deletes a category; its waypoints stay, without a category. */
    suspend fun deleteCategory(context: Context, id: Int): ApiResult<Unit> =
        sessionManager.authorized(context) { token -> api.deleteCategory(token, id) }.onSuccess {
            _categories.update { list -> list.filter { it.id != id } }
            _waypoints.update { list -> list.map { if (it.category?.id == id) it.copy(category = null) else it } }
        }

    /** Creates a tag, or renames the one with [id]. */
    suspend fun saveTag(context: Context, id: Int?, input: TagInput): ApiResult<Tag> =
        sessionManager.authorized(context) { token -> api.saveTag(token, id, input) }.onSuccess { saved ->
            _tags.update { list -> (list.filter { it.id != saved.id } + saved).sortedBy { it.name.lowercase() } }
            _waypoints.update { list ->
                list.map { waypoint -> waypoint.copy(tags = waypoint.tags.map { if (it.id == saved.id) saved else it }) }
            }
        }

    /** Deletes a tag; its waypoints stay. */
    suspend fun deleteTag(context: Context, id: Int): ApiResult<Unit> =
        sessionManager.authorized(context) { token -> api.deleteTag(token, id) }.onSuccess {
            _tags.update { list -> list.filter { it.id != id } }
            _waypoints.update { list -> list.map { waypoint -> waypoint.copy(tags = waypoint.tags.filter { it.id != id }) } }
        }

    /** Scales the image at [uri] down and adds it to the waypoint with [waypointId]. */
    suspend fun addPhoto(context: Context, waypointId: Int, uri: Uri): ApiResult<Photo> {
        val jpeg = try {
            PhotoScaler.jpeg(context, uri)
        } catch (e: IOException) {
            return ApiResult.Failure(uiText(R.string.photo_unreadable, e.message.orEmpty()))
        }
        return sessionManager.authorized(context) { token -> api.uploadPhoto(token, waypointId, jpeg) }.onSuccess { photo ->
            updateWaypoint(waypointId) { it.copy(photos = it.photos + photo) }
        }
    }

    suspend fun deletePhoto(context: Context, waypointId: Int, photoId: Int): ApiResult<Unit> =
        sessionManager.authorized(context) { token -> api.deletePhoto(token, photoId) }.onSuccess {
            updateWaypoint(waypointId) { waypoint -> waypoint.copy(photos = waypoint.photos.filter { it.id != photoId }) }
        }

    /** Forgets the diary, on the phone too, e.g. after signing out. */
    suspend fun clear() {
        _waypoints.value = emptyList()
        _categories.value = emptyList()
        _tags.value = emptyList()
        _syncedAt.value = null
        cache.clear()
    }

    /** On success: [apply] to the lists and keep them on the phone. */
    private suspend fun <T> ApiResult<T>.onSuccess(apply: (T) -> Unit): ApiResult<T> {
        if (this is ApiResult.Success) {
            apply(value)
            persist()
        }
        return this
    }

    private suspend fun persist() {
        val now = Instant.now()
        _syncedAt.value = now
        val owner = owner ?: return
        cache.save(owner, CachedDiary(_waypoints.value, _categories.value, _tags.value, now))
    }

    private fun updateWaypoint(id: Int, change: (Waypoint) -> Waypoint) =
        _waypoints.update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun ApiResult<*>.withoutValue(): ApiResult<Unit> = when (this) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Unauthorized -> this
        is ApiResult.Failure -> this
    }
}
