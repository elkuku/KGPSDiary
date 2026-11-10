package org.elkuku.kgpsdiary.auth

import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeGoogleAuthTest {
    private val context = ContextWrapper(null)

    @Test
    fun producesTheServersFakeTokenFormat() = runTest {
        val session = FakeGoogleAuth("dev@example.com").signIn(context)

        // FakeGoogleIdTokenVerifier::token('fake-dev@example.com', 'dev@example.com') in web
        assertEquals("fake|fake-dev@example.com|dev@example.com|verified", session.idToken)
        assertEquals("dev@example.com", session.email)
    }

    @Test
    fun signsInSilentlyOnlyAfterASignIn() = runTest {
        val auth = FakeGoogleAuth("dev@example.com")
        assertEquals(SilentSignIn.NoAccount, auth.signInSilently(context))

        auth.signIn(context)
        assertEquals("dev@example.com", (auth.signInSilently(context) as SilentSignIn.Signed).session.email)

        auth.signOut(context)
        assertEquals(SilentSignIn.NoAccount, auth.signInSilently(context))
    }
}
