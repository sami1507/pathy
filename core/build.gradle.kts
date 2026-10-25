import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The PATHY domain logic: GPS filtering, route following, OpenStreetMap parsing, trail grading, GPX.
// Plain Kotlin on the JVM - no Android, no Mapbox, no Firebase - so it builds and tests anywhere (including
// a CI machine with no secrets) and the build itself keeps Android types out of it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Keep build output off the synced project folder (same as the app module).
layout.buildDirectory.set(
    file("${System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")}/pathy-build/core")
)

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
