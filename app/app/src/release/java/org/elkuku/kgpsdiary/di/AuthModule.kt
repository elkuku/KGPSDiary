package org.elkuku.kgpsdiary.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.elkuku.kgpsdiary.BuildConfig
import org.elkuku.kgpsdiary.auth.CredentialManagerGoogleAuth
import org.elkuku.kgpsdiary.auth.GoogleAuth

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun googleAuth(): GoogleAuth = CredentialManagerGoogleAuth(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
}
