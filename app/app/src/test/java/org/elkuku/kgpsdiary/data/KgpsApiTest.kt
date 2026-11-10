package org.elkuku.kgpsdiary.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.UiText
import org.elkuku.kgpsdiary.data.KgpsApi.Companion.kgpsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KgpsApiTest {
    private var lastAuthorization: String? = null
    private var lastUrl: String? = null
    private var lastBody: String? = null
    private var lastContentType: String? = null
    private val requestedUrls = mutableListOf<String>()

    private fun api(respond: suspend MockRequestHandleScope.() -> HttpResponseData): KgpsApi {
        val engine = MockEngine { request ->
            lastAuthorization = request.headers[HttpHeaders.Authorization]
            lastUrl = request.url.toString()
            requestedUrls += request.url.toString()
            lastBody = (request.body as? TextContent)?.text
            lastContentType = request.body.contentType?.toString()
            respond()
        }
        return KgpsApi(HttpClient(engine) { install(ContentNegotiation) { kgpsJson() } }, "https://diary.test/")
    }

    @Test
    fun meSendsTheTokenAndReadsJsonLd() = runTest {
        val api = api {
            respond(
                """{"@context":"/api/contexts/Me","@id":"/api/me","@type":"Me","id":7,"identifier":"you@example.com","role":"ROLE_ADMIN"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/ld+json; charset=utf-8"),
            )
        }

        val result = api.me("token-1")

        assertEquals(ApiResult.Success(Me(7, "you@example.com", "ROLE_ADMIN")), result)
        assertTrue((result as ApiResult.Success).value.isAdmin)
        assertEquals("Bearer token-1", lastAuthorization)
        assertEquals("https://diary.test/api/me", lastUrl)
    }

    @Test
    fun unauthorizedCarriesTheServersDetail() = runTest {
        val api = api {
            respond(
                """{"type":"/errors/401","title":"An error occurred","status":401,"detail":"Invalid ID token."}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }

        assertEquals(ApiResult.Unauthorized(UiText.Plain("Invalid ID token.")), api.me("bad"))
    }

    @Test
    fun otherErrorsAreFailures() = runTest {
        val api = api { respondError(HttpStatusCode.InternalServerError, "oops") }

        assertEquals(ApiResult.Failure(UiText.Res(R.string.error_server, listOf(500))), api.me("token"))
    }

    @Test
    fun networkErrorsAreFailures() = runTest {
        val api = api { throw IOException("no route") }

        assertEquals(ApiResult.Failure(UiText.Res(R.string.error_network, listOf("no route"))), api.me("token"))
    }

    @Test
    fun waypointsAreLoadedPageByPage() = runTest {
        val api = api {
            val page2 = lastUrl.orEmpty().endsWith("page=2")
            respond(
                if (page2) {
                    """{"member":[{"id":2,"title":"B","latitude":2.0,"longitude":3.0}],"totalItems":2}"""
                } else {
                    """{"member":[{"@id":"/api/waypoints/1","id":1,"title":"A","description":"Text","latitude":-0.5,"longitude":-78.5,"visitedAt":"2026-10-31","category":{"@id":"/api/categories/3","id":3,"name":"Food","color":"#ff8800"},"tags":[{"id":7,"name":"Lunch"}],"createdAt":"2026-11-10T12:00:00+00:00","updatedAt":"2026-11-10T12:00:00+00:00"}],"totalItems":2,"view":{"@id":"/api/waypoints?itemsPerPage=1000&page=1","next":"/api/waypoints?itemsPerPage=1000&page=2"}}"""
                },
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/ld+json"),
            )
        }

        val result = api.waypoints("token")

        assertTrue(result is ApiResult.Success)
        val waypoints = (result as ApiResult.Success).value
        assertEquals(listOf(1, 2), waypoints.map { it.id })
        assertEquals(Category(3, "Food", "#ff8800"), waypoints[0].category)
        assertEquals(listOf(Tag(7, "Lunch")), waypoints[0].tags)
        assertEquals(java.time.LocalDate.of(2026, 10, 31), waypoints[0].visitedOn)
        assertEquals(null, waypoints[1].visitedOn)
        assertEquals(
            listOf("https://diary.test/api/waypoints?itemsPerPage=1000", "https://diary.test/api/waypoints?itemsPerPage=1000&page=2"),
            requestedUrls,
        )
    }

    @Test
    fun createSendsJsonLdWithNulls() = runTest {
        val api = api {
            respond(
                """{"id":5,"title":"New","latitude":1.0,"longitude":2.0}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/ld+json"),
            )
        }

        val result = api.createWaypoint("token", WaypointInput("New", "", 1.0, 2.0, null, null, listOf("/api/tags/7")))

        assertEquals(5, (result as ApiResult.Success).value.id)
        assertEquals("https://diary.test/api/waypoints", lastUrl)
        assertTrue(lastContentType.orEmpty().startsWith("application/ld+json"))
        assertEquals(
            """{"title":"New","description":"","latitude":1.0,"longitude":2.0,"visitedAt":null,"category":null,"tags":["/api/tags/7"]}""",
            lastBody,
        )
    }

    @Test
    fun updateIsAMergePatch() = runTest {
        val api = api {
            respond("""{"id":5,"title":"X","latitude":1.0,"longitude":2.0}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/ld+json"))
        }

        api.updateWaypoint("token", 5, WaypointInput("X", "", 1.0, 2.0, "2026-01-02", "/api/categories/3", emptyList()))

        assertEquals("https://diary.test/api/waypoints/5", lastUrl)
        assertTrue(lastContentType.orEmpty().startsWith("application/merge-patch+json"))
    }

    @Test
    fun violationsBecomeTheMessage() = runTest {
        val api = api {
            respond(
                """{"status":422,"detail":"latitude: This value should be between -90 and 90.","violations":[{"propertyPath":"latitude","message":"This value should be between -90 and 90."}]}""",
                HttpStatusCode.UnprocessableEntity,
                headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }

        val result = api.createWaypoint("token", WaypointInput("A", "", 95.0, 0.0, null, null, emptyList()))

        assertEquals(ApiResult.Failure(UiText.Plain("latitude: This value should be between -90 and 90.")), result)
    }

    @Test
    fun deleteAnswersNoContent() = runTest {
        val api = api { respond("", HttpStatusCode.NoContent) }

        assertEquals(ApiResult.Success(Unit), api.deleteWaypoint("token", 5))
        assertEquals("https://diary.test/api/waypoints/5", lastUrl)
    }

    @Test
    fun createsAndChangesCategories() = runTest {
        val api = api {
            respond("""{"id":3,"name":"Food","color":"#1565c0","icon":"restaurant"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/ld+json"))
        }

        val created = api.saveCategory("token", null, CategoryInput("Food", "#1565c0", "restaurant"))

        assertEquals(ApiResult.Success(Category(3, "Food", "#1565c0", "restaurant")), created)
        assertEquals("https://diary.test/api/categories", lastUrl)
        assertEquals("""{"name":"Food","color":"#1565c0","icon":"restaurant"}""", lastBody)

        api.saveCategory("token", 3, CategoryInput("Food", "#1565c0", null))
        assertEquals("https://diary.test/api/categories/3", lastUrl)
        assertTrue(lastContentType.orEmpty().startsWith("application/merge-patch+json"))
    }

    @Test
    fun createsAndDeletesTags() = runTest {
        val api = api {
            if (lastUrl.orEmpty().endsWith("/7")) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respond("""{"id":7,"name":"Lunch"}""", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/ld+json"))
            }
        }

        assertEquals(ApiResult.Success(Tag(7, "Lunch")), api.saveTag("token", null, TagInput("Lunch")))
        assertEquals(ApiResult.Success(Unit), api.deleteTag("token", 7))
        assertEquals("https://diary.test/api/tags/7", lastUrl)
    }
}
