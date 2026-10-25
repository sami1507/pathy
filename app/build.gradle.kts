import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kapt)
    alias(libs.plugins.google.gms.google.services)
}

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

// The project lives in OneDrive, whose sync client converts/locks files under app/build and makes
// Gradle fail with "Unable to delete directory" / AccessDenied. Build outputs are generated, so keep
// them in a local folder outside the synced tree. (Remove this line to use the default app/build.)
layout.buildDirectory.set(
    file("${System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")}/pathy-build/app")
)
// Mapbox Maps SDK needs a PUBLIC token (pk.*) at runtime. It is compiled into the app, so anyone can
// read it: a secret token (sk.*) must never be used here. Set MAPBOX_PUBLIC_TOKEN in local.properties.
// (The secret MAPBOX_DOWNLOADS_TOKEN is only used by settings.gradle.kts to download the SDK.)
// Third-party API keys (OpenAI, Perspective, OpenWeather) are NOT bundled in the app: they are Firebase
// secrets used only by the Cloud Functions in /functions.
val mapboxPublicToken: String = run {
    val candidates = listOf("MAPBOX_PUBLIC_TOKEN", "MAPBOX_ACCESS_TOKEN") // the second is the old name
        // local.properties on a developer machine; an environment variable on a build server
        .mapNotNull { key -> (localProps.getProperty(key) ?: System.getenv(key))?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it } }
    val usable = candidates.firstOrNull { (_, value) -> value.startsWith("pk.") }
    val secret = candidates.firstOrNull { (_, value) -> value.startsWith("sk.") }
    if (secret != null && secret.first == "MAPBOX_ACCESS_TOKEN") {
        logger.warn(
            "WARNING: MAPBOX_ACCESS_TOKEN in local.properties is a SECRET token (sk.*). It is ignored and NOT " +
                "put in the app. Create a public token (pk.*) at https://account.mapbox.com and save it as " +
                "MAPBOX_PUBLIC_TOKEN. Then revoke the secret token if it was ever shipped in an APK."
        )
    }
    usable?.second ?: ""
}
// The Mapbox SDK is downloaded with a secret token; without it the app cannot be built. (The :core module can.)
val mapboxDownloadsToken: String = (localProps.getProperty("MAPBOX_DOWNLOADS_TOKEN")
    ?: providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull).orEmpty()
gradle.taskGraph.whenReady {
    if (mapboxDownloadsToken.isBlank() && allTasks.any { it.project.path == ":app" && !it.name.startsWith("clean") }) {
        throw GradleException(
            "MAPBOX_DOWNLOADS_TOKEN is missing. Add it to local.properties (see HOW_TO_COMPILE_AND_RUN.md). " +
                "To work on the Android-free logic only, run: ./gradlew :core:test"
        )
    }
}

// A release without a public token would ship a map that cannot load, so stop it early.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { it.name.contains("Release", ignoreCase = true) && (it.name.startsWith("assemble") || it.name.startsWith("bundle")) }
    if (buildsRelease && mapboxPublicToken.isEmpty()) {
        throw GradleException("Release build needs MAPBOX_PUBLIC_TOKEN (a pk.* token) in local.properties.")
    }
}

android {
    namespace = "com.example.pathy"
    compileSdk = 35

    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        applicationId = "com.example.pathy"
        // 26: walkietalkie/ConnectionMode.kt and AudioReceiver.kt are @RequiresApi(O), and
        // PremiumHomeScreen uses java.time. On Android 7.x those paths would crash.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "mapbox_access_token", mapboxPublicToken)
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The language can be changed inside the app, so every language must ship in the bundle.
    // With the default per-language split, Google Play would install only the device language.
    bundle {
        language {
            enableSplit = false
        }
    }

    testOptions {
        // Robolectric renders Compose on the JVM, which needs Android resources on the test classpath
        unitTests.isIncludeAndroidResources = true
    }

    // ✅ حل مشاكل duplicate native libs/resources (خصوصًا مع WebRTC)
    packaging {
        jniLibs {
            pickFirsts += listOf(
                "**/libc++_shared.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Lifecycle ViewModel Compose - CRITICAL for viewModel()
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Mapbox - Keep your version (11.12.0) or use 11.8.0 for more stability
    implementation("com.mapbox.maps:android:11.8.0")
    implementation("com.mapbox.common:common:11.8.0")
    implementation("com.mapbox.extension:maps-compose:11.8.0")

    // Local server / Coroutines / JSON
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") // ✅ UPDATED
    implementation("com.google.code.gson:gson:2.11.0") // ✅ UPDATED

    // Nearby / Google
    implementation("com.google.android.gms:play-services-nearby:19.3.0") // ✅ UPDATED
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0") // ✅ UPDATED

    // ✅ WebRTC - Your Infobip version is excellent, keep it!
    implementation("com.infobip:google-webrtc:1.0.45036")

    // Lottie / Accompanist / AppCompat / Coil
    implementation("com.airbnb.android:lottie-compose:6.6.0")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")
    implementation("com.google.accompanist:accompanist-pager:0.36.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.36.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // Network stack
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Keep 4.x for stability
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Firebase BOM - Your version is newer, keep it!
    implementation(platform("com.google.firebase:firebase-bom:33.13.0")) // ✅ Keep your version
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    // Callable Cloud Functions: Scout AI (OpenAI), moderation, phone-login lookup
    implementation("com.google.firebase:firebase-functions")

    // Encrypted local storage for sensitive values (emergency contacts)
    implementation("androidx.security:security-crypto:1.0.0")

    // DataStore & time
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.jakewharton.threetenabp:threetenabp:1.4.7") // ✅ UPDATED

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    kapt("androidx.room:room-compiler:2.7.1")
    // Background upload of recorded outings once there is a connection
    // The domain logic lives in its own Android-free module
    implementation(project(":core"))
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Reads the rotation stored in a photo, so a picture taken upright is not uploaded sideways
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Tests
    testImplementation(libs.junit)
    // Design screenshot tests (JVM, no emulator needed)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

apply(plugin = "com.google.gms.google-services")
