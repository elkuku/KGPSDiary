package org.elkuku.kgpsdiary.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import javax.inject.Named
import javax.inject.Singleton
import org.elkuku.kgpsdiary.BuildConfig
import org.elkuku.kgpsdiary.data.KgpsApi
import org.elkuku.kgpsdiary.data.KgpsApi.Companion.kgpsJson

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { kgpsJson() }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    @Provides
    @Named(KgpsApi.SERVER_BASE_URL)
    fun serverBaseUrl(): String = BuildConfig.SERVER_BASE_URL
}
