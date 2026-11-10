package org.elkuku.kgpsdiary.ui.edit

import android.content.Context
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
import org.elkuku.kgpsdiary.data.DiaryRepository
import org.elkuku.kgpsdiary.data.Tag
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
}

data class EditWaypointUiState(
    /** Null for a new waypoint. */
    val id: Int? = null,
    val form: WaypointForm = WaypointForm(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val saving: Boolean = false,
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

    val state: StateFlow<EditWaypointUiState> = combine(local, diary.categories, diary.tags) { state, categories, tags ->
        state.copy(categories = categories, tags = tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun update(change: (WaypointForm) -> WaypointForm) = local.update { it.copy(form = change(it.form)) }

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
                is ApiResult.Success -> local.update { it.copy(saving = false, saved = true) }
                is ApiResult.Unauthorized -> local.update {
                    it.copy(saving = false, message = result.message ?: uiText(R.string.error_unauthorized))
                }
                is ApiResult.Failure -> local.update { it.copy(saving = false, message = result.message) }
            }
        }
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
            latitude = route.latitude?.let { "%.6f".format(java.util.Locale.ROOT, it) }.orEmpty(),
            longitude = route.longitude?.let { "%.6f".format(java.util.Locale.ROOT, it) }.orEmpty(),
        )
    }
}
