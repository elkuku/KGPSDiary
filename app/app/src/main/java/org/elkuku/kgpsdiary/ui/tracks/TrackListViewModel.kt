package org.elkuku.kgpsdiary.ui.tracks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.OffsetDateTime
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
import org.elkuku.kgpsdiary.data.Track
import org.elkuku.kgpsdiary.track.TrackVisibility
import org.elkuku.kgpsdiary.uiText

/** What the list shows of a track. */
data class TrackSummary(val startedAt: OffsetDateTime?, val kilometers: Double, val duration: Duration) {
    companion object {
        fun of(track: Track): TrackSummary {
            val start = runCatching { OffsetDateTime.parse(track.startedAt) }.getOrNull()
            val end = runCatching { OffsetDateTime.parse(track.endedAt) }.getOrNull()
            val duration = if (start != null && end != null && !end.isBefore(start)) Duration.between(start, end) else Duration.ZERO
            return TrackSummary(start, track.distance / 1000, duration)
        }
    }
}

data class TrackListUiState(
    val tracks: List<Track> = emptyList(),
    /** Tracks the map leaves out. */
    val hidden: Set<Int> = emptySet(),
    /** The track whose actions are shown. */
    val selected: Track? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
)

@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val diary: DiaryRepository,
    private val visibility: TrackVisibility,
) : ViewModel() {
    private val local = MutableStateFlow(TrackListUiState())

    val state: StateFlow<TrackListUiState> = combine(local, diary.tracks, visibility.hidden) { state, tracks, hidden ->
        // The selection follows renames, and goes when the track does
        state.copy(tracks = tracks, hidden = hidden, selected = state.selected?.let { selected -> tracks.find { it.id == selected.id } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackListUiState())

    /** Shows the track on the map, or hides it there. */
    fun toggleVisible(id: Int) = visibility.toggle(id)

    fun select(id: Int?) = local.update { it.copy(selected = id?.let(diary::track)) }

    fun rename(context: Context, id: Int, name: String) = run(uiText(R.string.track_renamed)) { diary.renameTrack(context, id, name) }

    fun delete(context: Context, id: Int) = run(uiText(R.string.track_deleted)) {
        diary.deleteTrack(context, id).also {
            if (it is ApiResult.Success) {
                local.update { state -> state.copy(selected = null) }
                // Nothing left to hide
                visibility.setVisible(id, true)
            }
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun run(success: UiText, call: suspend () -> ApiResult<*>) {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            val result = call()
            local.update {
                it.copy(
                    busy = false,
                    message = when (result) {
                        is ApiResult.Success -> success
                        is ApiResult.Unauthorized -> result.message ?: uiText(R.string.error_unauthorized)
                        is ApiResult.Failure -> result.message
                    },
                )
            }
        }
    }
}
