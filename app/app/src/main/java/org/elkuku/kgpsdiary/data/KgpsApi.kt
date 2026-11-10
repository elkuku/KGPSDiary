package org.elkuku.kgpsdiary.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.UiText
import org.elkuku.kgpsdiary.uiText

/** API Platform speaks JSON-LD; its errors are RFC 7807 problems. */
val LdJson = ContentType("application", "ld+json")
val ProblemJson = ContentType("application", "problem+json")
val MergePatchJson = ContentType("application", "merge-patch+json")

/** The signed-in user, `GET /api/me`. [role] is `ROLE_USER` or `ROLE_ADMIN`. */
@Serializable
data class Me(val id: Int, val identifier: String, val role: String) {
    val isAdmin: Boolean get() = role == "ROLE_ADMIN"
}

@Serializable
private data class Problem(val detail: String = "", val violations: List<Violation> = emptyList())

@Serializable
private data class Violation(val propertyPath: String = "", val message: String = "")

/** The web app's API under [baseUrl]/api, see docs/developers/api.md. */
class KgpsApi @Inject constructor(
    private val client: HttpClient,
    @param:Named(SERVER_BASE_URL) private val baseUrl: String,
) {
    private val root get() = baseUrl.trimEnd('/')

    suspend fun me(idToken: String): ApiResult<Me> = request { client.get("$root/api/me") { auth(idToken) } }

    /** All of the user's waypoints, page by page. */
    suspend fun waypoints(idToken: String): ApiResult<List<Waypoint>> = all(idToken, "/api/waypoints?itemsPerPage=1000")

    suspend fun categories(idToken: String): ApiResult<List<Category>> = all(idToken, "/api/categories")

    suspend fun tags(idToken: String): ApiResult<List<Tag>> = all(idToken, "/api/tags")

    suspend fun createWaypoint(idToken: String, input: WaypointInput): ApiResult<Waypoint> = request {
        client.post("$root/api/waypoints") {
            auth(idToken)
            contentType(LdJson)
            setBody(input)
        }
    }

    suspend fun updateWaypoint(idToken: String, id: Int, input: WaypointInput): ApiResult<Waypoint> = request {
        client.patch("$root/api/waypoints/$id") {
            auth(idToken)
            contentType(MergePatchJson)
            setBody(input)
        }
    }

    suspend fun deleteWaypoint(idToken: String, id: Int): ApiResult<Unit> = delete(idToken, "/api/waypoints/$id")

    suspend fun saveCategory(idToken: String, id: Int?, input: CategoryInput): ApiResult<Category> = request {
        if (id == null) {
            client.post("$root/api/categories") { auth(idToken); contentType(LdJson); setBody(input) }
        } else {
            client.patch("$root/api/categories/$id") { auth(idToken); contentType(MergePatchJson); setBody(input) }
        }
    }

    suspend fun deleteCategory(idToken: String, id: Int): ApiResult<Unit> = delete(idToken, "/api/categories/$id")

    suspend fun saveTag(idToken: String, id: Int?, input: TagInput): ApiResult<Tag> = request {
        if (id == null) {
            client.post("$root/api/tags") { auth(idToken); contentType(LdJson); setBody(input) }
        } else {
            client.patch("$root/api/tags/$id") { auth(idToken); contentType(MergePatchJson); setBody(input) }
        }
    }

    suspend fun deleteTag(idToken: String, id: Int): ApiResult<Unit> = delete(idToken, "/api/tags/$id")

    /** Adds a photo ([jpeg], at most 20 MB) to the waypoint with [waypointId]. */
    suspend fun uploadPhoto(idToken: String, waypointId: Int, jpeg: ByteArray): ApiResult<Photo> = request {
        client.submitFormWithBinaryData(
            url = "$root/api/waypoints/$waypointId/photos",
            formData = formData {
                append(
                    "file",
                    jpeg,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                    },
                )
            },
        ) { auth(idToken) }
    }

    suspend fun deletePhoto(idToken: String, id: Int): ApiResult<Unit> = delete(idToken, "/api/photos/$id")

    private suspend fun delete(idToken: String, path: String): ApiResult<Unit> =
        when (val result = send { client.delete("$root$path") { auth(idToken) } }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Unauthorized -> result
            is ApiResult.Failure -> result
        }

    private suspend inline fun <reified T> all(idToken: String, firstPage: String): ApiResult<List<T>> {
        val items = mutableListOf<T>()
        var page: String? = firstPage

        while (page != null) {
            val path = page
            when (val result = request<CollectionPage<T>> { client.get("$root$path") { auth(idToken) } }) {
                is ApiResult.Success -> {
                    items += result.value.member
                    page = result.value.view?.next?.takeIf { it != path }
                }
                is ApiResult.Unauthorized -> return result
                is ApiResult.Failure -> return result
            }
        }

        return ApiResult.Success(items)
    }

    private fun HttpRequestBuilder.auth(idToken: String) {
        bearerAuth(idToken)
        accept(LdJson)
    }

    private suspend inline fun <reified T> request(crossinline call: suspend () -> HttpResponse): ApiResult<T> =
        when (val result = send(call)) {
            is ApiResult.Success -> try {
                ApiResult.Success(result.value.body<T>())
            } catch (e: SerializationException) {
                ApiResult.Failure(uiText(R.string.error_response, e.message.orEmpty()))
            } catch (e: IllegalArgumentException) {
                // Ktor wraps JSON errors
                ApiResult.Failure(uiText(R.string.error_response, e.message.orEmpty()))
            }
            is ApiResult.Unauthorized -> result
            is ApiResult.Failure -> result
        }

    /** The response if it is a success; else the server's message. */
    private suspend inline fun send(crossinline call: suspend () -> HttpResponse): ApiResult<HttpResponse> = try {
        val response = call()
        when {
            response.status.isSuccess() -> ApiResult.Success(response)
            response.status == HttpStatusCode.Unauthorized -> ApiResult.Unauthorized(response.problem())
            else -> ApiResult.Failure(response.problem() ?: uiText(R.string.error_server, response.status.value))
        }
    } catch (e: IOException) {
        ApiResult.Failure(uiText(R.string.error_network, e.message.orEmpty()), offline = true)
    }

    /** The violations of a 422, else the `detail` of a problem answer. */
    private suspend fun HttpResponse.problem(): UiText? = try {
        val problem = body<Problem>()
        when {
            problem.violations.isNotEmpty() -> UiText.Plain(
                problem.violations.joinToString("\n") { listOf(it.propertyPath, it.message).filter(String::isNotBlank).joinToString(": ") },
            )
            problem.detail.isNotBlank() -> UiText.Plain(problem.detail)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val SERVER_BASE_URL = "serverBaseUrl"

        private val json = Json { ignoreUnknownKeys = true }

        /** Answers in JSON, JSON-LD or problem+json, ignoring JSON-LD's @-keys; merge patches. */
        fun ContentNegotiationConfig.kgpsJson() {
            json(json)
            json(json, LdJson)
            json(json, ProblemJson)
            json(json, MergePatchJson)
        }
    }
}
