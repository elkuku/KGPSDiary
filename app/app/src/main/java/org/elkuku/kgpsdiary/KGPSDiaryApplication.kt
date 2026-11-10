package org.elkuku.kgpsdiary

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.elkuku.kgpsdiary.auth.SessionManager

@HiltAndroidApp
class KGPSDiaryApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var sessionManager: SessionManager

    /** Coil sends the session's ID token to the server, which serves the photos to their owner only. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val server = BuildConfig.SERVER_BASE_URL.toHttpUrlOrNull()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val token = sessionManager.session.value?.idToken
                val ours = server != null && request.url.host == server.host && request.url.port == server.port
                chain.proceed(
                    if (ours && token != null) request.newBuilder().header("Authorization", "Bearer $token").build() else request,
                )
            }
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
