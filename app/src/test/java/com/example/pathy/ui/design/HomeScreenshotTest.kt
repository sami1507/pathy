package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.screens.UltraModernHomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen in every state and language, written as PNGs for review, plus checks that each
 * shortcut is reachable and large enough to tap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class HomeScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) =
        rule.captureTo(name, dark) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) { content() }
        }

    @Composable
    private fun home() = UltraModernHomeScreen(userName = "Ahmad", totalDistance = 12500f, totalActivities = 7, currentLevel = 2)

    @Test fun english() = shot("home_new_en") { home() }

    @Test fun englishDark() = shot("home_new_en_dark", dark = true) { home() }

    @Test fun largestFont() = shot("home_new_font2_en", fontScale = 2f) { home() }

    @Test fun empty() = shot("home_new_empty_en") { UltraModernHomeScreen(userName = "Ahmad") }

    @Test fun loading() = shot("home_new_loading_en") { UltraModernHomeScreen(userName = "Ahmad", statsLoading = true) }

    @Test fun failed() = shot("home_new_error_en") { UltraModernHomeScreen(userName = "Ahmad", statsError = true) }

    @Test fun signedOut() = shot("home_new_signedout_en") { UltraModernHomeScreen(signedIn = false) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun arabic() = shot("home_new_ar") {
        UltraModernHomeScreen(userName = "أحمد", totalDistance = 12500f, totalActivities = 7, currentLevel = 2)
    }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun hebrew() = shot("home_new_he") {
        UltraModernHomeScreen(userName = "אחמד", totalDistance = 12500f, totalActivities = 2, currentLevel = 2)
    }

    @Test fun everyShortcutIsAButtonThatCallsBack() {
        val calls = mutableListOf<String>()
        rule.setContent {
            com.example.pathy.ui.theme.TEMPATHYTheme {
                UltraModernHomeScreen(
                    userName = "Ahmad",
                    onStartTracking = { calls += "track" },
                    onOpenMap = { calls += "map" },
                    onDiscoverTrails = { calls += "discover" },
                    onViewHistory = { calls += "history" },
                    onOpenScout = { calls += "scout" },
                    onViewProfile = { calls += "profile" }
                )
            }
        }
        val labels = listOf(
            R.string.home_track to "track",
            R.string.home_map to "map",
            R.string.home_discover to "discover",
            R.string.home_history to "history",
            R.string.home_scout to "scout",
            R.string.home_profile to "profile"
        )
        for ((label, call) in labels) {
            val text = rule.activity.getString(label)
            val button = rule.onAllNodes(
                SemanticsMatcher("button labelled $text") { node ->
                    node.config.getOrNull(SemanticsProperties.Role) == Role.Button &&
                        (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
                            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true)
                }
            ).onFirst()
            button.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            rule.runOnIdle { assertEquals(call, calls.last()) }
        }
        assertEquals(labels.size, calls.size)
    }
}
