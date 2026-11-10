package org.elkuku.kgpsdiary.ui.tracks

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.elkuku.kgpsdiary.auth.CountingAuth
import org.elkuku.kgpsdiary.auth.MemoryAccountStore
import org.elkuku.kgpsdiary.auth.SessionManager
import org.elkuku.kgpsdiary.data.DiaryRepository
import org.elkuku.kgpsdiary.data.KgpsApi
import org.elkuku.kgpsdiary.data.KgpsApi.Companion.kgpsJson
import org.elkuku.kgpsdiary.data.cache.CachedDiary
import org.elkuku.kgpsdiary.data.cache.DiaryCache
import org.elkuku.kgpsdiary.track.HiddenTrackStore
import org.elkuku.kgpsdiary.track.TrackVisibility
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The actions of a track, as the track list and a tap on a track on the map use them. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackListViewModelTest {
    private object NoCache : DiaryCache {
        override suspend fun load(owner: String): CachedDiary? = null

        override suspend fun save(owner: String, diary: CachedDiary) = Unit

        override suspend fun clear() = Unit
    }

    private class MemoryStore(var ids: Set<Int> = emptySet()) : HiddenTrackStore {
        override fun load() = ids

        override fun save(ids: Set<Int>) {
            this.ids = ids
        }
    }

    private val context: Context = ContextWrapper(null)
    private val store = MemoryStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(): TrackListViewModel {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Delete) return@MockEngine respond("", HttpStatusCode.NoContent)
            val body = when (request.url.encodedPath) {
                "/api/tracks" -> """{"member":[{"id":4,"name":"Walk","points":[[0.0,-78.5,1793000000],[0.01,-78.5,1793000600]]},{"id":5,"name":"Hike"}]}"""
                else -> """{"member":[]}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/ld+json"))
        }
        val api = KgpsApi(HttpClient(engine) { install(ContentNegotiation) { kgpsJson() } }, "https://diary.test")
        val sessions = SessionManager(CountingAuth(), MemoryAccountStore())
        sessions.signIn(context)
        val diary = DiaryRepository(sessions, api, NoCache)
        diary.refresh(context)

        return TrackListViewModel(diary, TrackVisibility(store)).also { viewModel ->
            // The state is only kept up while someone watches it
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        }
    }

    @Test
    fun aTappedTrackIsSelectedByItsId() = runTest {
        val viewModel = viewModel()

        viewModel.select(5)
        assertEquals("Hike", viewModel.state.value.selected?.name)

        viewModel.select(null)
        assertNull(viewModel.state.value.selected)
    }

    @Test
    fun hidingFromTheSheetLeavesTheTrackOffTheMap() = runTest {
        val viewModel = viewModel()
        viewModel.select(4)

        // What the map's sheet does
        viewModel.select(null)
        viewModel.toggleVisible(4)

        assertNull(viewModel.state.value.selected)
        assertEquals(setOf(4), viewModel.state.value.hidden)
        assertEquals(setOf(4), store.ids)
    }

    @Test
    fun aDeletedTrackClosesTheSheetAndIsNoLongerHidden() = runTest {
        val viewModel = viewModel()
        viewModel.toggleVisible(4)
        viewModel.select(4)

        viewModel.delete(context, 4)
        // The server is called off the main thread: wait for the answer
        viewModel.state.first { it.message != null }

        assertNull(viewModel.state.value.selected)
        assertEquals(listOf("Hike"), viewModel.state.value.tracks.map { it.name })
        assertEquals(emptySet<Int>(), store.ids)
    }
}
