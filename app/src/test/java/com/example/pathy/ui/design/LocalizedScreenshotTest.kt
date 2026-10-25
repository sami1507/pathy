package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.pathy.screens.BookmarksScreen
import com.example.pathy.screens.EmergencySOSScreen
import com.example.pathy.screens.OnboardingScreen
import com.example.pathy.screens.SettingsScreen
import com.example.pathy.screens.UltraModernHomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens under each app language. The device locale selects values-ar / values-iw, the
 * theme picks the script-aware type scale, and Compose flips the layout for the RTL languages.
 * Only screens that do not need Firebase or Mapbox can be rendered on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalizedScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, content: @Composable () -> Unit) =
        composeRule.captureTo(name, dark = false, content = content)

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun onboardingArabic() = shot("l10n_onboarding_ar") { OnboardingScreen(onComplete = {}) }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun onboardingHebrew() = shot("l10n_onboarding_he") { OnboardingScreen(onComplete = {}) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun settingsArabic() = shot("l10n_settings_ar") { SettingsScreen(onBack = {}) }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun settingsHebrew() = shot("l10n_settings_he") { SettingsScreen(onBack = {}) }

    @Composable
    private fun bookmarks() = BookmarksScreen(
        bookmarks = System.currentTimeMillis().let { now ->
            listOf(
                com.example.pathy.bookmarks.Bookmark("1", "Wadi Rum Ridge", now - 30_000L, emptyMap()),
                com.example.pathy.bookmarks.Bookmark("2", "מסלול נחל עמוד", now - 2 * 60_000L, emptyMap()),
                com.example.pathy.bookmarks.Bookmark("3", "مسار وادي رم", now - 5 * 3_600_000L, emptyMap()),
                com.example.pathy.bookmarks.Bookmark("4", "Carmel Forest Path", now - 11 * 86_400_000L, emptyMap())
            )
        }
    )

    @Composable
    private fun sos() = EmergencySOSScreen(
        emergencyContacts = listOf(
            com.example.pathy.emergency.EmergencyContact("Sara", "0501234567", "Sister"),
            com.example.pathy.emergency.EmergencyContact("عمر", "0527654321", "Friend")
        )
    )

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun bookmarksArabic() = shot("l10n_bookmarks_ar") { bookmarks() }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun bookmarksHebrew() = shot("l10n_bookmarks_he") { bookmarks() }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun sosArabic() = shot("l10n_sos_ar") { sos() }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun sosHebrew() = shot("l10n_sos_he") { sos() }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun homeArabic() = shot("l10n_home_ar") {
        UltraModernHomeScreen(userName = "أحمد", totalDistance = 12500f, totalActivities = 7, currentLevel = 2)
    }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun homeHebrew() = shot("l10n_home_he") {
        UltraModernHomeScreen(userName = "אחמד", totalDistance = 12500f, totalActivities = 2, currentLevel = 2)
    }
}
