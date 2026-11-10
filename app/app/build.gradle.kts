import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun localProperty(name: String, default: String = ""): String =
    localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: default

android {
    namespace = "org.elkuku.kgpsdiary"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.elkuku.kgpsdiary"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${localProperty("google.serverClientId")}\"")
        // The Mapbox SDK reads its token from this string resource
        resValue("string", "mapbox_access_token", localProperty("mapbox.accessToken"))
    }

    signingConfigs {
        // Release key from local.properties; without it assembleRelease builds an unsigned APK
        if (localProperty("release.storeFile").isNotBlank()) {
            create("release") {
                storeFile = file(localProperty("release.storeFile"))
                storePassword = localProperty("release.storePassword")
                keyAlias = localProperty("release.keyAlias")
                keyPassword = localProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "SERVER_BASE_URL", "\"${localProperty("server.baseUrl.debug", "http://10.0.2.2:8000")}\"")
            // Fake sign-in without Google (needs APP_FAKE_GOOGLE_TOKENS=1 on the dev server)
            buildConfigField("String", "FAKE_SIGN_IN_EMAIL", "\"${localProperty("debug.fakeSignInEmail")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "SERVER_BASE_URL", "\"${localProperty("server.baseUrl.release")}\"")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    // The app's languages for Android 13+'s per-app language setting (res/resources.properties)
    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    // Photos, with the bearer token for the server's images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.mapbox.android)
    implementation(libs.mapbox.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
