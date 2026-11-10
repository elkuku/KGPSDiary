package org.elkuku.kgpsdiary.ui.map

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
import org.elkuku.kgpsdiary.data.DiaryRepository
import org.elkuku.kgpsdiary.data.Waypoint
import org.elkuku.kgpsdiary.uiText

data class MapUiState(
    val waypoints: List<Waypoint> = emptyList(),
    /** The waypoint whose details are shown. */
    val selected: Waypoint? = null,
    val loading: Boolean = false,
    /** True once the waypoints were loaded: the map then fits them. */
    val loaded: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(private val diary: DiaryRepository) : ViewModel() {
    private val local = MutableStateFlow(MapUiState())

    val state: StateFlow<MapUiState> = combine(local, diary.waypoints) { state, waypoints ->
        state.copy(
            waypoints = waypoints,
            // The selection follows changes, and goes when the waypoint does
            selected = state.selected?.let { selected -> waypoints.find { it.id == selected.id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun load(context: Context) {
        if (local.value.loading) return
        viewModelScope.launch {
            local.update { it.copy(loading = true) }
            val result = diary.refresh(context)
            local.update {
                it.copy(
                    loading = false,
                    loaded = true,
                    message = when (result) {
                        is ApiResult.Success -> null
                        is ApiResult.Unauthorized -> result.message ?: uiText(R.string.error_unauthorized)
                        is ApiResult.Failure -> result.message
                    },
                )
            }
        }
    }

    fun select(id: Int?) = local.update { state -> state.copy(selected = id?.let { diary.waypoint(it) }) }

    fun delete(context: Context, id: Int) {
        viewModelScope.launch {
            when (val result = diary.delete(context, id)) {
                is ApiResult.Success -> local.update { it.copy(selected = null, message = uiText(R.string.waypoint_deleted)) }
                is ApiResult.Unauthorized -> local.update { it.copy(message = result.message ?: uiText(R.string.error_unauthorized)) }
                is ApiResult.Failure -> local.update { it.copy(message = result.message) }
            }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }
}
