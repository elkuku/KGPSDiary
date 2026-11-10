package org.elkuku.kgpsdiary.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
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
import org.elkuku.kgpsdiary.data.Track
import org.elkuku.kgpsdiary.data.Waypoint
import org.elkuku.kgpsdiary.data.filterWaypoints
import org.elkuku.kgpsdiary.track.RecordedPoint
import org.elkuku.kgpsdiary.track.TrackRecorder
import org.elkuku.kgpsdiary.uiText

data class MapUiState(
    val waypoints: List<Waypoint> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** The filters: only this category, and this tag (null: all). */
    val categoryId: Int? = null,
    val tagId: Int? = null,
    /** The waypoint whose details are shown. */
    val selected: Waypoint? = null,
    val loading: Boolean = false,
    /** True once the waypoints were loaded: the map then fits them. */
    val loaded: Boolean = false,
    /** The server could not be reached: the map shows what the phone kept, from [syncedAt]. */
    val offline: Boolean = false,
    val syncedAt: Instant? = null,
    val tracks: List<Track> = emptyList(),
    /** A track is being recorded; [recordedPoints] are its points so far. */
    val recording: Boolean = false,
    val recordedPoints: List<RecordedPoint> = emptyList(),
    val savingTrack: Boolean = false,
    val message: UiText? = null,
) {
    /** Recorded, stopped and not uploaded yet: the app asks to save or discard it. */
    val unsavedTrack: Boolean get() = !recording && recordedPoints.isNotEmpty()

    /** The waypoints the filters let through, for the map. */
    val visibleWaypoints: List<Waypoint> get() = filterWaypoints(waypoints, categoryId, tagId)
    val filtered: Boolean get() = categoryId != null || tagId != null
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val diary: DiaryRepository,
    private val recorder: TrackRecorder,
) : ViewModel() {
    private val local = MutableStateFlow(MapUiState())

    private val diaryState = combine(
        local,
        diary.waypoints,
        diary.categories,
        diary.tags,
        diary.syncedAt,
    ) { state, waypoints, categories, tags, syncedAt ->
        state.copy(
            syncedAt = syncedAt,
            waypoints = waypoints,
            categories = categories,
            tags = tags,
            // A filter on a deleted category or tag ends
            categoryId = state.categoryId?.takeIf { id -> categories.any { it.id == id } },
            tagId = state.tagId?.takeIf { id -> tags.any { it.id == id } },
            // The selection follows changes, and goes when the waypoint does
            selected = state.selected?.let { selected -> waypoints.find { it.id == selected.id } },
        )
    }

    val state: StateFlow<MapUiState> = combine(diaryState, diary.tracks, recorder.recording, recorder.points) { state, tracks, recording, points ->
        state.copy(tracks = tracks, recording = recording, recordedPoints = points)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    /** Starts recording; the location permission must be granted. */
    fun startRecording() = recorder.start()

    fun stopRecording() = recorder.stop()

    /** Uploads the stopped recording as [name]; it stays on the phone if that fails. */
    fun saveTrack(context: Context, name: String) {
        val points = state.value.recordedPoints
        if (points.size < 2 || local.value.savingTrack) return

        viewModelScope.launch {
            local.update { it.copy(savingTrack = true) }
            val result = diary.saveTrack(context, TrackRecorder.trackInput(name.trim(), points))
            if (result is ApiResult.Success) recorder.discard()
            local.update {
                it.copy(
                    savingTrack = false,
                    message = when (result) {
                        is ApiResult.Success -> uiText(R.string.track_saved)
                        is ApiResult.Unauthorized -> result.message ?: uiText(R.string.error_unauthorized)
                        is ApiResult.Failure -> uiText(R.string.track_not_saved, result.message)
                    },
                )
            }
        }
    }

    fun discardRecording() {
        viewModelScope.launch { recorder.discard() }
    }

    fun load(context: Context) {
        if (local.value.loading) return
        viewModelScope.launch {
            local.update { it.copy(loading = true) }
            // What the phone kept, at once; then the server's
            diary.restore()
            if (diary.waypoints.value.isNotEmpty()) local.update { it.copy(loaded = true) }

            val result = diary.refresh(context)
            local.update {
                it.copy(
                    loading = false,
                    loaded = true,
                    offline = result is ApiResult.Failure && result.offline,
                    message = when (result) {
                        is ApiResult.Success -> null
                        is ApiResult.Unauthorized -> result.message ?: uiText(R.string.error_unauthorized)
                        // Offline the notice on the map says it
                        is ApiResult.Failure -> result.message.takeUnless { result.offline }
                    },
                )
            }
        }
    }

    /** Shows only [categoryId]'s waypoints; the same again shows all. */
    fun filterCategory(categoryId: Int) = local.update { it.copy(categoryId = categoryId.takeIf { id -> id != it.categoryId }) }

    fun filterTag(tagId: Int) = local.update { it.copy(tagId = tagId.takeIf { id -> id != it.tagId }) }

    fun clearFilters() = local.update { it.copy(categoryId = null, tagId = null) }

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
