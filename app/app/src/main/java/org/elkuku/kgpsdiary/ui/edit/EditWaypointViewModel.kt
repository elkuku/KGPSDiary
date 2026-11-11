package org.elkuku.kgpsdiary.ui.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.UiText
import org.elkuku.kgpsdiary.data.ApiResult
import org.elkuku.kgpsdiary.data.Category
import org.elkuku.kgpsdiary.data.CategoryInput
import org.elkuku.kgpsdiary.data.DiaryRepository
import org.elkuku.kgpsdiary.data.Photo
import org.elkuku.kgpsdiary.data.Tag
import org.elkuku.kgpsdiary.data.TagInput
import org.elkuku.kgpsdiary.data.WaypointInput
import org.elkuku.kgpsdiary.ui.EditWaypointRoute
import org.elkuku.kgpsdiary.uiText

/** The form; coordinates are text while they are typed. */
data class WaypointForm(
    val title: String = "",
    val description: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val visitedOn: LocalDate? = LocalDate.now(),
    val categoryId: Int? = null,
    val tagIds: Set<Int> = emptySet(),
) {
    val latitudeValue: Double? get() = latitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val longitudeValue: Double? get() = longitude.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val isValid: Boolean get() = title.isNotBlank() && latitudeValue != null && longitudeValue != null

    /** Where the map's pin points. */
    fun at(latitude: Double, longitude: Double) = copy(latitude = coordinate(latitude), longitude = coordinate(longitude))

    fun toggleTag(id: Int) = copy(tagIds = if (id in tagIds) tagIds - id else tagIds + id)

    companion object {
        fun coordinate(value: Double): String = "%.6f".format(java.util.Locale.ROOT, value)
    }
}

data class EditWaypointUiState(
    /** Null for a new waypoint. */
    val id: Int? = null,
    val form: WaypointForm = WaypointForm(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** The waypoint's photos on the server. */
    val photos: List<Photo> = emptyList(),
    /** Picked photos, uploaded when the waypoint is saved. */
    val pendingPhotos: List<Uri> = emptyList(),
    val saving: Boolean = false,
    /** A new category or tag is being saved. */
    val creating: Boolean = false,
    /** True once saved: the screen closes. */
    val saved: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class EditWaypointViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val diary: DiaryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<EditWaypointRoute>()
    private val local = MutableStateFlow(EditWaypointUiState(id = route.id, form = initialForm(route)))

    val state: StateFlow<EditWaypointUiState> = combine(local, diary.categories, diary.tags, diary.waypoints) { state, categories, tags, waypoints ->
        state.copy(categories = categories, tags = tags, photos = waypoints.find { it.id == state.id }?.photos.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun update(change: (WaypointForm) -> WaypointForm) = local.update { it.copy(form = change(it.form)) }

    /** The map was moved: the waypoint goes where its pin points. */
    fun moveTo(latitude: Double, longitude: Double) = update { it.at(latitude, longitude) }

    /** Saves a new category and selects it; [onCreated] closes the dialog. */
    fun createCategory(context: Context, input: CategoryInput, onCreated: () -> Unit) = create(onCreated) {
        diary.saveCategory(context, null, input).also { result ->
            if (result is ApiResult.Success) update { it.copy(categoryId = result.value.id) }
        }
    }

    /** Saves a new tag and selects it. */
    fun createTag(context: Context, input: TagInput, onCreated: () -> Unit) = create(onCreated) {
        diary.saveTag(context, null, input).also { result ->
            if (result is ApiResult.Success) update { it.copy(tagIds = it.tagIds + result.value.id) }
        }
    }

    private fun create(onCreated: () -> Unit, call: suspend () -> ApiResult<*>) {
        if (local.value.creating) return
        viewModelScope.launch {
            local.update { it.copy(creating = true) }
            val result = call()
            local.update {
                it.copy(
                    creating = false,
                    message = when (result) {
                        is ApiResult.Success -> null
                        is ApiResult.Unauthorized -> result.message ?: uiText(R.string.error_unauthorized)
                        is ApiResult.Failure -> result.message
                    },
                )
            }
            if (result is ApiResult.Success) onCreated()
        }
    }

    fun addPhotos(uris: List<Uri>) = local.update { it.copy(pendingPhotos = (it.pendingPhotos + uris).distinct()) }

    fun removePendingPhoto(uri: Uri) = local.update { it.copy(pendingPhotos = it.pendingPhotos - uri) }

    /** Deletes a photo of the saved waypoint right away. */
    fun deletePhoto(context: Context, photoId: Int) {
        val id = local.value.id ?: return
        viewModelScope.launch {
            when (val result = diary.deletePhoto(context, id, photoId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Unauthorized -> local.update { it.copy(message = result.message ?: uiText(R.string.error_unauthorized)) }
                is ApiResult.Failure -> local.update { it.copy(message = result.message) }
            }
        }
    }

    fun save(context: Context) {
        val state = local.value
        val form = state.form
        val latitude = form.latitudeValue
        val longitude = form.longitudeValue
        if (state.saving || !form.isValid || latitude == null || longitude == null) return

        val input = WaypointInput(
            title = form.title.trim(),
            description = form.description,
            latitude = latitude,
            longitude = longitude,
            visitedAt = form.visitedOn?.toString(),
            category = diary.categories.value.find { it.id == form.categoryId }?.iri,
            tags = diary.tags.value.filter { it.id in form.tagIds }.map { it.iri },
        )

        viewModelScope.launch {
            local.update { it.copy(saving = true) }
            when (val result = diary.save(context, state.id, input)) {
                is ApiResult.Success -> uploadPending(context, result.value.id)
                is ApiResult.Unauthorized -> local.update {
                    it.copy(saving = false, message = result.message ?: uiText(R.string.error_unauthorized))
                }
                is ApiResult.Failure -> local.update { it.copy(saving = false, message = result.message) }
            }
        }
    }

    /**
     * Uploads the picked photos to the saved waypoint, one after another. Photos that fail stay
     * picked, and the screen stays open on the saved waypoint, to try again.
     */
    private suspend fun uploadPending(context: Context, waypointId: Int) {
        local.update { it.copy(id = waypointId) }
        var failure: UiText? = null

        for (uri in local.value.pendingPhotos) {
            when (val result = diary.addPhoto(context, waypointId, uri)) {
                is ApiResult.Success -> local.update { it.copy(pendingPhotos = it.pendingPhotos - uri) }
                is ApiResult.Unauthorized -> failure = result.message ?: uiText(R.string.error_unauthorized)
                is ApiResult.Failure -> failure = result.message
            }
        }

        local.update { it.copy(saving = false, saved = failure == null, message = failure) }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun initialForm(route: EditWaypointRoute): WaypointForm {
        val waypoint = route.id?.let(diary::waypoint)
        if (waypoint != null) {
            return WaypointForm(
                title = waypoint.title,
                description = waypoint.description,
                latitude = waypoint.latitude.toString(),
                longitude = waypoint.longitude.toString(),
                visitedOn = waypoint.visitedOn,
                categoryId = waypoint.category?.id,
                tagIds = waypoint.tags.map { it.id }.toSet(),
            )
        }
        return WaypointForm(
            latitude = route.latitude?.let(WaypointForm::coordinate).orEmpty(),
            longitude = route.longitude?.let(WaypointForm::coordinate).orEmpty(),
        )
    }
}
