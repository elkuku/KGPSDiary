package org.elkuku.kgpsdiary.track

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.elkuku.kgpsdiary.data.TrackInput
import org.elkuku.kgpsdiary.data.cache.DiaryDatabase

/**
 * The track being recorded: [RecordingService] adds GPS points while [recording]. The points stay
 * on the phone until the track is uploaded or discarded, also across restarts.
 */
@Singleton
class TrackRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    database: DiaryDatabase,
) {
    private val dao = database.recordedPoints()
    private val preferences = context.getSharedPreferences("recording", Context.MODE_PRIVATE)

    private val _recording = MutableStateFlow(preferences.getBoolean(RECORDING, false))
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    val points: Flow<List<RecordedPoint>> = dao.all()

    fun start() {
        setRecording(true)
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
    }

    fun stop() {
        setRecording(false)
        context.stopService(Intent(context, RecordingService::class.java))
    }

    /** Adds a GPS fix, unless it is less than [MIN_STEP] meters from the last one. */
    suspend fun add(latitude: Double, longitude: Double, time: Long) {
        val last = dao.last()
        if (last != null && TrackMath.between(last.latitude, last.longitude, latitude, longitude) < MIN_STEP) return
        dao.insert(RecordedPoint(latitude = latitude, longitude = longitude, time = time))
    }

    suspend fun discard() = dao.clear()

    private fun setRecording(recording: Boolean) {
        _recording.value = recording
        preferences.edit { putBoolean(RECORDING, recording) }
    }

    companion object {
        private const val RECORDING = "recording"

        /** Meters a point must be from the previous one; less is GPS noise while standing. */
        const val MIN_STEP = 3.0

        /** The points for the upload: [latitude, longitude, Unix seconds]. */
        fun trackInput(name: String, points: List<RecordedPoint>) = TrackInput(
            name = name,
            points = points.map { JsonArray(listOf(JsonPrimitive(it.latitude), JsonPrimitive(it.longitude), JsonPrimitive(it.time / 1000))) },
        )
    }
}
