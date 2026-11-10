package org.elkuku.kgpsdiary.auth

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.elkuku.kgpsdiary.data.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hands out token-1, token-2, … ; silent sign-ins answer [silent]. */
class CountingAuth(var silent: Silent = Silent.SIGNED) : GoogleAuth {
    enum class Silent { SIGNED, NO_ACCOUNT, UNAVAILABLE }

    var issued = 0

    override suspend fun signIn(context: Context) = next()

    override suspend fun signInSilently(context: Context): SilentSignIn = when (silent) {
        Silent.SIGNED -> SilentSignIn.Signed(next())
        Silent.NO_ACCOUNT -> SilentSignIn.NoAccount
        Silent.UNAVAILABLE -> SilentSignIn.Unavailable
    }

    override suspend fun signOut(context: Context) = Unit

    private fun next() = GoogleSession("token-${++issued}", "you@example.com", "You")
}

class MemoryAccountStore(var account: Account? = null) : AccountStore {
    override fun load() = account

    override fun save(account: Account) {
        this.account = account
    }

    override fun clear() {
        account = null
    }
}

class SessionManagerTest {
    private val context: Context = ContextWrapper(null)
    private val store = MemoryAccountStore()

    @Test
    fun withoutAccountCallsAreUnauthorized() = runTest {
        val manager = SessionManager(CountingAuth(), store)
        var called = false

        val result = manager.authorized(context) { called = true; ApiResult.Success(1) }

        assertEquals(ApiResult.Unauthorized(null), result)
        assertEquals(false, called)
    }

    @Test
    fun signingInRemembersTheAccount() = runTest {
        val manager = SessionManager(CountingAuth(), store)
        manager.signIn(context)

        assertEquals(Account("you@example.com", "You"), store.account)
        assertEquals(ApiResult.Success("token-1"), manager.authorized(context) { ApiResult.Success(it) })

        manager.signOut(context)
        assertNull(store.account)
        assertNull(manager.account.value)
    }

    @Test
    fun retriesOnceWithAFreshTokenAfter401() = runTest {
        val manager = SessionManager(CountingAuth(), store)
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
    fun signsOutWhenNoAccountIsLeft() = runTest {
        val auth = CountingAuth()
        val manager = SessionManager(auth, store)
        manager.signIn(context)
        auth.silent = CountingAuth.Silent.NO_ACCOUNT

        val result = manager.authorized(context) { ApiResult.Unauthorized(null) }

        assertEquals(ApiResult.Unauthorized(null), result)
        assertNull(manager.account.value)
        assertNull(store.account)
    }

    @Test
    fun signsOutWhenTheFreshTokenIsRejectedToo() = runTest {
        val manager = SessionManager(CountingAuth(), store)
        manager.signIn(context)
        var calls = 0

        manager.authorized(context) { calls++; ApiResult.Unauthorized(null) }

        assertEquals(2, calls)
        assertNull(manager.account.value)
    }

    @Test
    fun startsOfflineWithTheRememberedAccount() = runTest {
        store.account = Account("you@example.com", "You")
        val auth = CountingAuth(CountingAuth.Silent.UNAVAILABLE)
        val manager = SessionManager(auth, store)

        assertEquals(Account("you@example.com", "You"), manager.restore(context))
        assertNull(manager.session.value)

        // Calls fail as offline, without signing out
        val result = manager.authorized(context) { ApiResult.Success(it) }
        assertTrue(result is ApiResult.Failure && result.offline)
        assertEquals(Account("you@example.com", "You"), manager.account.value)

        // Once Google answers again, calls get a token
        auth.silent = CountingAuth.Silent.SIGNED
        assertEquals(ApiResult.Success("token-1"), manager.authorized(context) { ApiResult.Success(it) })
    }

    @Test
    fun restoringWithoutAccountForgetsIt() = runTest {
        store.account = Account("old@example.com", null)
        val manager = SessionManager(CountingAuth(CountingAuth.Silent.NO_ACCOUNT), store)

        assertNull(manager.restore(context))
        assertNull(store.account)
    }

    @Test
    fun offlineWithoutRememberedAccountIsSignedOut() = runTest {
        val manager = SessionManager(CountingAuth(CountingAuth.Silent.UNAVAILABLE), store)

        assertNull(manager.restore(context))
    }
}
