package org.elkuku.kgpsdiary.auth

import android.content.ContextWrapper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(auth.signInSilently(context))

        auth.signIn(context)
        assertEquals("dev@example.com", auth.signInSilently(context)?.email)

        auth.signOut(context)
        assertNull(auth.signInSilently(context))
    }
}
