package org.elkuku.kgpsdiary.ui.manage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.elkuku.kgpsdiary.data.Tag
import org.elkuku.kgpsdiary.data.TagInput
import org.elkuku.kgpsdiary.uiText

data class ManageUiState(
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** Waypoints per category and tag ID. */
    val categoryCounts: Map<Int, Int> = emptyMap(),
    val tagCounts: Map<Int, Int> = emptyMap(),
    val busy: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class ManageViewModel @Inject constructor(private val diary: DiaryRepository) : ViewModel() {
    private val local = MutableStateFlow(ManageUiState())

    val state: StateFlow<ManageUiState> = combine(local, diary.categories, diary.tags, diary.waypoints) { state, categories, tags, waypoints ->
        state.copy(
            categories = categories,
            tags = tags,
            categoryCounts = waypoints.mapNotNull { it.category?.id }.groupingBy { it }.eachCount(),
            tagCounts = waypoints.flatMap { waypoint -> waypoint.tags.map { it.id } }.groupingBy { it }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

    /** Saves a new category ([id] null) or changes one; true once saved. */
    fun saveCategory(context: Context, id: Int?, input: CategoryInput, onSaved: () -> Unit) =
        run({ diary.saveCategory(context, id, input) }, onSaved)

    fun deleteCategory(context: Context, id: Int) = run({ diary.deleteCategory(context, id) })

    fun saveTag(context: Context, id: Int?, input: TagInput, onSaved: () -> Unit) = run({ diary.saveTag(context, id, input) }, onSaved)

    fun deleteTag(context: Context, id: Int) = run({ diary.deleteTag(context, id) })

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun run(call: suspend () -> ApiResult<*>, onSuccess: () -> Unit = {}) {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val result = call()
            local.update {
                it.copy(
                    busy = false,
                    message = when (result) {
                        is ApiResult.Success -> null
                        is ApiResult.Unauthorized -> result.message ?: uiText(R.string.error_unauthorized)
                        is ApiResult.Failure -> result.message
                    },
                )
            }
            if (result is ApiResult.Success) onSuccess()
        }
    }
}
