package org.elkuku.kgpsdiary.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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

/** The signed-in user, `GET /api/me`. [role] is `ROLE_USER` or `ROLE_ADMIN`. */
@Serializable
data class Me(val id: Int, val identifier: String, val role: String) {
    val isAdmin: Boolean get() = role == "ROLE_ADMIN"
}

@Serializable
private data class Problem(val detail: String = "")

/** The web app's API under [baseUrl]/api. */
class KgpsApi @Inject constructor(
    private val client: HttpClient,
    @param:Named(SERVER_BASE_URL) private val baseUrl: String,
) {
    suspend fun me(idToken: String): ApiResult<Me> = request {
        client.get("${baseUrl.trimEnd('/')}/api/me") {
            bearerAuth(idToken)
            accept(LdJson)
        }
    }

    private suspend inline fun <reified T> request(send: () -> HttpResponse): ApiResult<T> = try {
        val response = send()
        when {
            response.status.isSuccess() -> ApiResult.Success(response.body<T>())
            response.status == HttpStatusCode.Unauthorized -> ApiResult.Unauthorized(response.problem())
            else -> ApiResult.Failure(
                response.problem() ?: uiText(R.string.error_server, response.status.value),
            )
        }
    } catch (e: IOException) {
        ApiResult.Failure(uiText(R.string.error_network, e.message.orEmpty()))
    } catch (e: SerializationException) {
        ApiResult.Failure(uiText(R.string.error_response, e.message.orEmpty()))
    }

    /** The server's `detail` of a problem answer. */
    private suspend fun HttpResponse.problem(): UiText? = try {
        body<Problem>().detail.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val SERVER_BASE_URL = "serverBaseUrl"

        private val json = Json { ignoreUnknownKeys = true }

        /** Answers in JSON, JSON-LD or problem+json, ignoring JSON-LD's @-keys. */
        fun ContentNegotiationConfig.kgpsJson() {
            json(json)
            json(json, LdJson)
            json(json, ProblemJson)
        }
    }
}
