// Mapbox SDK download token (secret, "sk.") is kept in local.properties (gitignored),
// not in the tracked gradle.properties.
val mapboxDownloadsToken: String = java.util.Properties()
    .also { props -> file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) } }
    .getProperty("MAPBOX_DOWNLOADS_TOKEN")
    ?: providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
    // Optional here so the Android-free :core module can be built and tested without any secret (for example
    // on a CI machine). Building the app without it fails with a clear message, see app/build.gradle.kts.
    ?: ""


pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://api.mapbox.com/downloads/v2/releases/maven") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
                    credentials {
                        username = "mapbox"
                        password = mapboxDownloadsToken
                    }
                    authentication { create<BasicAuthentication>("basic") }
                }
            }
            filter {
                includeGroupByRegex("com\\.mapbox(\\..+)?")
                includeGroup("com.mapbox.maps")
                includeGroup("com.mapbox.common")
                includeGroup("com.mapbox.extension")
            }
        }
    }
}


rootProject.name = "Pathy"
include(":core")
include(":app")
