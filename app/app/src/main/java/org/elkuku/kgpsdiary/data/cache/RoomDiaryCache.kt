package org.elkuku.kgpsdiary.data.cache

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.elkuku.kgpsdiary.data.Category
import org.elkuku.kgpsdiary.data.Tag
import org.elkuku.kgpsdiary.data.Waypoint

/**
 * One cached record as the API's JSON: the server's models change without a database migration
 * here, and a record that no longer reads is simply left out.
 */
@Entity(tableName = "cached_record", primaryKeys = ["owner", "kind", "id"])
data class CachedRecord(val owner: String, val kind: String, val id: Long, val json: String)

@Dao
interface CachedRecordDao {
    @Query("SELECT * FROM cached_record WHERE owner = :owner")
    suspend fun all(owner: String): List<CachedRecord>

    @Insert
    suspend fun insert(records: List<CachedRecord>)

    @Query("DELETE FROM cached_record")
    suspend fun deleteAll()

    /** The owner's records, replacing everything: the phone keeps one account's diary. */
    @Transaction
    suspend fun replace(records: List<CachedRecord>) {
        deleteAll()
        insert(records)
    }
}

@Database(entities = [CachedRecord::class], version = 1, exportSchema = false)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun cachedRecords(): CachedRecordDao

    companion object {
        const val NAME = "diary.db"
    }
}

class RoomDiaryCache @Inject constructor(private val database: DiaryDatabase) : DiaryCache {
    private val dao get() = database.cachedRecords()

    override suspend fun load(owner: String): CachedDiary? {
        val records = dao.all(owner).groupBy { it.kind }
        val savedAt = records[META]?.firstOrNull()?.json?.toLongOrNull() ?: return null

        return CachedDiary(
            waypoints = records[WAYPOINT].decode(Waypoint.serializer()).sortedWith(newestVisitFirst),
            categories = records[CATEGORY].decode(Category.serializer()).sortedBy { it.name.lowercase() },
            tags = records[TAG].decode(Tag.serializer()).sortedBy { it.name.lowercase() },
            savedAt = Instant.ofEpochMilli(savedAt),
        )
    }

    override suspend fun save(owner: String, diary: CachedDiary) {
        // Only one account's diary is kept: replace() deletes all others
        dao.replace(
            diary.waypoints.map { CachedRecord(owner, WAYPOINT, it.id.toLong(), json.encodeToString(Waypoint.serializer(), it)) } +
                diary.categories.map { CachedRecord(owner, CATEGORY, it.id.toLong(), json.encodeToString(Category.serializer(), it)) } +
                diary.tags.map { CachedRecord(owner, TAG, it.id.toLong(), json.encodeToString(Tag.serializer(), it)) } +
                CachedRecord(owner, META, 0, diary.savedAt.toEpochMilli().toString()),
        )
    }

    override suspend fun clear() = dao.deleteAll()

    private fun <T> List<CachedRecord>?.decode(serializer: KSerializer<T>): List<T> = orEmpty().mapNotNull { record ->
        try {
            json.decodeFromString(serializer, record.json)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val WAYPOINT = "waypoint"
        const val CATEGORY = "category"
        const val TAG = "tag"
        const val META = "meta"

        val json = Json { ignoreUnknownKeys = true }

        /** As the server sorts them: the newest visit first, then the newest waypoint. */
        val newestVisitFirst = compareByDescending<Waypoint> { it.visitedAt.orEmpty() }.thenByDescending { it.id }
    }
}
