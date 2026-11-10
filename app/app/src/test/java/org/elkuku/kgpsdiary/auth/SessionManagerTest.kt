package org.elkuku.kgpsdiary.auth

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.elkuku.kgpsdiary.data.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagerTest {
    /** Hands out token-1, token-2, … ; silent sign-in only while [silentAvailable]. */
    private class CountingAuth(var silentAvailable: Boolean = true) : GoogleAuth {
        var issued = 0

        override suspend fun signIn(context: Context) = next()

        override suspend fun signInSilently(context: Context) = if (silentAvailable) next() else null

        override suspend fun signOut(context: Context) = Unit

        private fun next() = GoogleSession("token-${++issued}", "you@example.com", null)
    }

    private val context: Context = ContextWrapper(null)

    @Test
    fun withoutSessionCallsAreUnauthorized() = runTest {
        val manager = SessionManager(CountingAuth())
        var called = false

        val result = manager.authorized(context) { called = true; ApiResult.Success(1) }

        assertEquals(ApiResult.Unauthorized(null), result)
        assertEquals(false, called)
    }

    @Test
    fun passesTheCurrentToken() = runTest {
        val manager = SessionManager(CountingAuth())
        manager.signIn(context)

        assertEquals(ApiResult.Success("token-1"), manager.authorized(context) { ApiResult.Success(it) })
    }

    @Test
    fun retriesOnceWithAFreshTokenAfter401() = runTest {
        val manager = SessionManager(CountingAuth())
        manager.signIn(context)
        val tokens = mutableListOf<String>()

        val result = manager.authorized(context) { token ->
            tokens += token
            if (token == "token-1") ApiResult.Unauthorized(null) else ApiResult.Success(token)
        }

        assertEquals(ApiResult.Success("token-2"), result)
        assertEquals(listOf("token-1", "token-2"), tokens)
        assertEquals("token-2", manager.session.value?.idToken)
    }

    @Test
    fun endsTheSessionWhenNoFreshTokenHelps() = runTest {
        val auth = CountingAuth()
        val manager = SessionManager(auth)
        manager.signIn(context)
        auth.silentAvailable = false

        val result = manager.authorized(context) { ApiResult.Unauthorized(null) }

        assertEquals(ApiResult.Unauthorized(null), result)
        assertNull(manager.session.value)
    }

    @Test
    fun endsTheSessionWhenTheFreshTokenIsRejectedToo() = runTest {
        val manager = SessionManager(CountingAuth())
        manager.signIn(context)
        var calls = 0

        manager.authorized(context) { calls++; ApiResult.Unauthorized(null) }

        assertEquals(2, calls)
        assertNull(manager.session.value)
    }
}
