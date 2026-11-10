package org.elkuku.kgpsdiary.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.elkuku.kgpsdiary.BuildConfig
import org.elkuku.kgpsdiary.auth.CredentialManagerGoogleAuth
import org.elkuku.kgpsdiary.auth.FakeGoogleAuth
import org.elkuku.kgpsdiary.auth.GoogleAuth

/** Debug builds use [FakeGoogleAuth] when `debug.fakeSignInEmail` is set in local.properties. */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun googleAuth(): GoogleAuth =
        if (BuildConfig.FAKE_SIGN_IN_EMAIL.isNotBlank()) {
            FakeGoogleAuth(BuildConfig.FAKE_SIGN_IN_EMAIL)
        } else {
            CredentialManagerGoogleAuth(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
        }
}
