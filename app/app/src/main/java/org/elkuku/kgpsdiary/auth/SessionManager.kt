package org.elkuku.kgpsdiary.auth

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.elkuku.kgpsdiary.data.ApiResult

/**
 * The signed-in Google account, and API calls with its ID token. Google ID tokens expire after an
 * hour: a call the server answers with 401 gets a fresh token silently and is retried once.
 */
@Singleton
class SessionManager @Inject constructor(private val googleAuth: GoogleAuth) {
    private val _session = MutableStateFlow<GoogleSession?>(null)
    val session: StateFlow<GoogleSession?> = _session.asStateFlow()

    /** Shows the account picker. */
    suspend fun signIn(context: Context): GoogleSession = googleAuth.signIn(context).also { _session.value = it }

    /** Signs in again with an account that already authorized the app; null without one. */
    suspend fun restore(context: Context): GoogleSession? =
        googleAuth.signInSilently(context).also { _session.value = it }

    suspend fun signOut(context: Context) {
        _session.value = null
        googleAuth.signOut(context)
    }

    /** [call] with the session's ID token; [ApiResult.Unauthorized] without a session. */
    suspend fun <T> authorized(context: Context, call: suspend (idToken: String) -> ApiResult<T>): ApiResult<T> {
        val current = _session.value ?: return ApiResult.Unauthorized(null)
        val result = call(current.idToken)

        if (result !is ApiResult.Unauthorized) {
            return result
        }

        val fresh = googleAuth.signInSilently(context)
        if (fresh == null) {
            _session.value = null
            return result
        }

        _session.value = fresh
        return call(fresh.idToken).also {
            if (it is ApiResult.Unauthorized) {
                _session.value = null
            }
        }
    }
}
