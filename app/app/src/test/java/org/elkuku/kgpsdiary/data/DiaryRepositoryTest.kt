package org.elkuku.kgpsdiary.data

import android.content.Context
import android.content.ContextWrapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.elkuku.kgpsdiary.auth.CountingAuth
import org.elkuku.kgpsdiary.auth.MemoryAccountStore
import org.elkuku.kgpsdiary.auth.SessionManager
import org.elkuku.kgpsdiary.data.KgpsApi.Companion.kgpsJson
import org.elkuku.kgpsdiary.data.cache.CachedDiary
import org.elkuku.kgpsdiary.data.cache.DiaryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryRepositoryTest {
    private class MemoryCache : DiaryCache {
        val diaries = mutableMapOf<String, CachedDiary>()

        override suspend fun load(owner: String) = diaries[owner]

        override suspend fun save(owner: String, diary: CachedDiary) {
            diaries.clear()
            diaries[owner] = diary
        }

        override suspend fun clear() = diaries.clear()
    }

    private val context: Context = ContextWrapper(null)
    private val cache = MemoryCache()
    private var online = true

    private val server = mapOf(
        "/api/waypoints" to """{"member":[{"id":1,"title":"From the server","latitude":0.0,"longitude":0.0}]}""",
        "/api/categories" to """{"member":[{"id":3,"name":"Food","color":"#1565c0"}]}""",
        "/api/tags" to """{"member":[{"id":7,"name":"Lunch"}]}""",
        "/api/tracks" to """{"member":[{"id":4,"name":"Walk","points":[[0.0,-78.5,1793000000],[0.01,-78.5,1793000600]],"distance":1111.9}]}""",
    )

    private fun repository(auth: CountingAuth = CountingAuth()): Pair<DiaryRepository, SessionManager> {
        val engine = MockEngine { request ->
            if (!online) throw IOException("offline")
            if (request.method == HttpMethod.Delete) return@MockEngine respond("", HttpStatusCode.NoContent)
            respond(server.getValue(request.url.encodedPath), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/ld+json"))
        }
        val api = KgpsApi(HttpClient(engine) { install(ContentNegotiation) { kgpsJson() } }, "https://diary.test")
        val sessions = SessionManager(auth, MemoryAccountStore())
        return DiaryRepository(sessions, api, cache) to sessions
    }

    private val savedDiary = CachedDiary(
        waypoints = listOf(Waypoint(9, "Kept on the phone", latitude = 1.0, longitude = 2.0)),
        categories = emptyList(),
        tags = emptyList(),
        savedAt = Instant.parse("2026-11-01T10:00:00Z"),
    )

    @Test
    fun refreshKeepsTheDiaryOnThePhone() = runTest {
        val (diary, sessions) = repository()
        sessions.signIn(context)

        assertEquals(ApiResult.Success(Unit), diary.refresh(context))

        val saved = cache.diaries.getValue("you@example.com")
        assertEquals(listOf("From the server"), saved.waypoints.map { it.title })
        assertEquals(listOf("Food"), saved.categories.map { it.name })
        assertEquals(listOf("Lunch"), saved.tags.map { it.name })
        assertEquals(listOf("Walk"), saved.tracks.map { it.name })
        assertEquals(listOf(4), diary.tracks.value.map { it.id })
        assertEquals(saved.savedAt, diary.syncedAt.value)
    }

    @Test
    fun offlineTheSavedDiaryStays() = runTest {
        cache.diaries["you@example.com"] = savedDiary
        val (diary, sessions) = repository()
        sessions.signIn(context)
        online = false

        diary.restore()
        val result = diary.refresh(context)

        assertTrue(result is ApiResult.Failure && result.offline)
        assertEquals(listOf("Kept on the phone"), diary.waypoints.value.map { it.title })
        assertEquals(savedDiary.savedAt, diary.syncedAt.value)
    }

    @Test
    fun anotherAccountDoesNotSeeTheSavedDiary() = runTest {
        cache.diaries["someone@example.com"] = savedDiary
        val (diary, sessions) = repository()
        sessions.signIn(context)

        diary.restore()

        assertEquals(emptyList<Waypoint>(), diary.waypoints.value)
        assertNull(diary.syncedAt.value)
    }

    @Test
    fun changesAreKeptOnThePhone() = runTest {
        val (diary, sessions) = repository()
        sessions.signIn(context)
        diary.refresh(context)

        diary.deleteTag(context, 7)

        assertEquals(emptyList<Tag>(), cache.diaries.getValue("you@example.com").tags)
    }

    @Test
    fun clearingForgetsThePhonesCopy() = runTest {
        val (diary, sessions) = repository()
        sessions.signIn(context)
        diary.refresh(context)

        diary.clear()

        assertTrue(cache.diaries.isEmpty())
        assertEquals(emptyList<Waypoint>(), diary.waypoints.value)
    }
}
