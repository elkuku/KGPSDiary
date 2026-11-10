package org.elkuku.kgpsdiary.data.cache

import java.time.Instant
import org.elkuku.kgpsdiary.data.Category
import org.elkuku.kgpsdiary.data.Tag
import org.elkuku.kgpsdiary.data.Track
import org.elkuku.kgpsdiary.data.Waypoint

/** The diary as last loaded from the server, and when. */
data class CachedDiary(
    val waypoints: List<Waypoint>,
    val categories: List<Category>,
    val tags: List<Tag>,
    val savedAt: Instant,
    val tracks: List<Track> = emptyList(),
)

/**
 * The diary on the phone, for showing it at once and without a connection. It belongs to one
 * account ([owner]: the email address); another account sees nothing of it.
 */
interface DiaryCache {
    suspend fun load(owner: String): CachedDiary?

    suspend fun save(owner: String, diary: CachedDiary)

    /** Forgets every account's diary, e.g. when signing out. */
    suspend fun clear()
}
