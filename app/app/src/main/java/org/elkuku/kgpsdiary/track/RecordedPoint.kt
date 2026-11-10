package org.elkuku.kgpsdiary.track

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A point of the track being recorded. Each is stored at once, so a recording survives the app
 * being closed or killed; [time] is in milliseconds since 1970.
 */
@Entity(tableName = "recorded_point")
data class RecordedPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val time: Long,
)

@Dao
interface RecordedPointDao {
    @Query("SELECT * FROM recorded_point ORDER BY time, id")
    fun all(): Flow<List<RecordedPoint>>

    @Query("SELECT * FROM recorded_point ORDER BY time DESC, id DESC LIMIT 1")
    suspend fun last(): RecordedPoint?

    @Insert
    suspend fun insert(point: RecordedPoint)

    @Query("DELETE FROM recorded_point")
    suspend fun clear()
}
