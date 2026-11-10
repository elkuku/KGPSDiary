package org.elkuku.kgpsdiary.auth

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.data.ApiResult
import org.elkuku.kgpsdiary.uiText

/**
 * The signed-in Google account, and API calls with its ID token.
 *
 * Google ID tokens expire after an hour: a call the server answers with 401 gets a fresh token
 * silently and is retried once. Without a connection the app starts with the remembered
 * [account] and no token; calls then fail as offline until Google can be asked again.
 */
@Singleton
class SessionManager @Inject constructor(
    private val googleAuth: GoogleAuth,
    private val accountStore: AccountStore,
) {
    private val _session = MutableStateFlow<GoogleSession?>(null)

    /** The current token, null while offline or signed out. */
    val session: StateFlow<GoogleSession?> = _session.asStateFlow()

    private val _account = MutableStateFlow<Account?>(null)

    /** Who is signed in, also offline; null when signed out. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    /** Shows the account picker. */
    suspend fun signIn(context: Context): GoogleSession = googleAuth.signIn(context).also(::start)

    /**
     * Signs in again with an account that already authorized the app; offline with the
     * remembered account. Null when signed out.
     */
    suspend fun restore(context: Context): Account? {
        when (val result = googleAuth.signInSilently(context)) {
            is SilentSignIn.Signed -> start(result.session)
            SilentSignIn.NoAccount -> forget()
            SilentSignIn.Unavailable -> _account.value = accountStore.load()
        }
        return _account.value
    }

    suspend fun signOut(context: Context) {
        forget()
        googleAuth.signOut(context)
    }

    /**
     * [call] with the session's ID token. [ApiResult.Unauthorized] when signed out, an offline
     * [ApiResult.Failure] when no token can be had.
     */
    suspend fun <T> authorized(context: Context, call: suspend (idToken: String) -> ApiResult<T>): ApiResult<T> {
        if (_account.value == null) return ApiResult.Unauthorized(null)

        val current = _session.value ?: when (val fresh = freshSession(context)) {
            is Fresh.Session -> fresh.session
            is Fresh.Failed -> return fresh.result
        }

        val result = call(current.idToken)
        if (result !is ApiResult.Unauthorized) return result

        return when (val fresh = freshSession(context)) {
            is Fresh.Session -> call(fresh.session.idToken).also { if (it is ApiResult.Unauthorized) forget() }
            is Fresh.Failed -> fresh.result
        }
    }

    private sealed interface Fresh {
        data class Session(val session: GoogleSession) : Fresh
        data class Failed(val result: ApiResult<Nothing>) : Fresh
    }

    /** A new token, without the account picker. */
    private suspend fun freshSession(context: Context): Fresh = when (val result = googleAuth.signInSilently(context)) {
        is SilentSignIn.Signed -> Fresh.Session(result.session.also(::start))
        SilentSignIn.NoAccount -> {
            forget()
            Fresh.Failed(ApiResult.Unauthorized(null))
        }
        SilentSignIn.Unavailable -> {
            _session.value = null
            Fresh.Failed(ApiResult.Failure(uiText(R.string.error_offline), offline = true))
        }
    }

    private fun start(session: GoogleSession) {
        val account = Account(session.email, session.displayName)
        _session.value = session
        _account.value = account
        accountStore.save(account)
    }

    private fun forget() {
        _session.value = null
        _account.value = null
        accountStore.clear()
    }
}
