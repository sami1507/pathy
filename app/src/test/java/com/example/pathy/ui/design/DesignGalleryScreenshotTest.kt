package com.example.pathy.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the design system on the JVM and writes PNGs to the folder given by the
 * `pathy.shots.dir` system property (default: the system temp folder), so the look can be reviewed
 * without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h1400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignGalleryScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun galleryLight() = capture("gallery_light", dark = false) { DesignGallery() }

    @Test
    fun homeLight() = capture("home_light", dark = false) {
        com.example.pathy.screens.UltraModernHomeScreen(
            userName = "Ahmad",
            totalDistance = 12500f,
            totalActivities = 7,
            currentLevel = 2
        )
    }

    @Test
    fun galleryDark() = capture("gallery_dark", dark = true) { DesignGallery() }

    @Test
    fun homeDark() = capture("home_dark", dark = true) { SampleHome() }

    @Test
    fun onboardingLight() = capture("onboarding_light", dark = false) {
        com.example.pathy.screens.OnboardingScreen(onComplete = {})
    }

    @Test
    fun onboardingDark() = capture("onboarding_dark", dark = true) {
        com.example.pathy.screens.OnboardingScreen(onComplete = {})
    }

    @Test
    fun bookmarksLight() = capture("bookmarks_light", dark = false) { SampleBookmarks() }

    @Test
    fun bookmarksDark() = capture("bookmarks_dark", dark = true) { SampleBookmarks() }

    @Test
    fun sosLight() = capture("sos_light", dark = false) { SampleSos() }

    @Test
    fun sosDark() = capture("sos_dark", dark = true) { SampleSos() }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) =
        composeRule.captureTo(name, dark, content)
}

@Composable
private fun DesignGallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MountainHero(
            title = "Wadi Rum Ridge",
            eyebrow = "Trail",
            subtitle = "Moderate · 8.4 km · Loop",
            content = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NatureTag(label = "Moderate", color = PathyColors.DifficultyModerate, onHero = true)
                    NatureTag(label = "Hiking", color = PathyColors.TrailHiking, onHero = true)
                }
            }
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NatureSectionHeader(title = "Stats", actionLabel = "See all", onAction = {})
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NatureStatTile(Icons.Filled.Straighten, "8.4 km", "Distance", Modifier.weight(1f))
                NatureStatTile(Icons.Filled.Timer, "2h 40m", "Time", Modifier.weight(1f))
                NatureStatTile(Icons.Filled.Terrain, "420 m", "Climb", Modifier.weight(1f))
            }

            NatureCard {
                Text("Trail card", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Body text sample: readable size and comfortable line height.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NatureTag(label = "Easy", color = PathyColors.DifficultyEasy)
                    NatureTag(label = "Hard", color = PathyColors.DifficultyHard)
                    NatureTag(label = "Extreme", color = PathyColors.DifficultyExtreme)
                }
            }

            Text("Headline Large", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("Title Medium", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("Label Medium · Secondary", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PathyColors.Primary, PathyColors.Secondary, PathyColors.Accent,
                    PathyColors.Highlight, PathyColors.Warning, PathyColors.Error
                ).forEach { color ->
                    Column(Modifier.weight(1f)) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(color, RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleHome() {
    com.example.pathy.screens.UltraModernHomeScreen(
        userName = "Ahmad",
        totalDistance = 12500f,
        totalActivities = 7,
        currentLevel = 2
    )
}

@Composable
private fun SampleBookmarks() {
    val now = System.currentTimeMillis()
    com.example.pathy.screens.BookmarksScreen(
        bookmarks = listOf(
            com.example.pathy.bookmarks.Bookmark("1", "Wadi Rum Ridge", now, emptyMap()),
            com.example.pathy.bookmarks.Bookmark("2", "Ein Gedi Loop", now - 86_400_000L, emptyMap()),
            com.example.pathy.bookmarks.Bookmark("3", "Carmel Forest Path", now - 172_800_000L, emptyMap())
        )
    )
}

@Composable
private fun SampleSos() {
    com.example.pathy.screens.EmergencySOSScreen(
        emergencyContacts = listOf(
            com.example.pathy.emergency.EmergencyContact("Sara", "0501234567", "Sister"),
            com.example.pathy.emergency.EmergencyContact("Omar", "0527654321", "Friend")
        )
    )
}
