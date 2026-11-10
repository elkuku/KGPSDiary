package org.elkuku.kgpsdiary.auth

import android.content.Context

/**
 * Debug only: "signs in" without Google, producing the fake token format that
 * web accepts in dev with APP_FAKE_GOOGLE_TOKENS=1
 * (see FakeGoogleIdTokenVerifier on the server).
 */
class FakeGoogleAuth(private val email: String) : GoogleAuth {
    private var signedIn = false

    override suspend fun signIn(context: Context): GoogleSession {
        signedIn = true
        return session()
    }

    override suspend fun signInSilently(context: Context): SilentSignIn =
        if (signedIn) SilentSignIn.Signed(session()) else SilentSignIn.NoAccount

    override suspend fun signOut(context: Context) {
        signedIn = false
    }

    private fun session() = GoogleSession(
        idToken = listOf("fake", "fake-$email", email, "verified").joinToString("|"),
        email = email,
        displayName = "Fake user",
    )
}
