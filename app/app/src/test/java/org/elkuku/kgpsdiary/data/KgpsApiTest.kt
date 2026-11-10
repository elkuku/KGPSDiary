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

    private fun api(respond: suspend MockRequestHandleScope.() -> HttpResponseData): KgpsApi {
        val engine = MockEngine { request ->
            lastAuthorization = request.headers[HttpHeaders.Authorization]
            lastUrl = request.url.toString()
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
}
