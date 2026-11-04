package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.example.pathy.R
import com.example.pathy.screens.OnboardingScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first screens a new user sees: they must fit a small phone with a large font and work with a thumb. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class OnboardingScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun english() = rule.captureTo("onboarding_en", false) { OnboardingScreen(onComplete = {}) }

    @Test fun dark() = rule.captureTo("onboarding_dark", true) { OnboardingScreen(onComplete = {}) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun arabic() = rule.captureTo("onboarding_ar", false) { OnboardingScreen(onComplete = {}) }

    @Test
    @Config(sdk = [34], qualifiers = "en-w320dp-h568dp-xhdpi")
    fun smallPhoneLargeFont() = rule.captureTo("onboarding_small_font2", false) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) { OnboardingScreen(onComplete = {}) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-w320dp-h568dp-xhdpi")
    fun nextStaysReachableOnASmallPhoneWithALargeFont() {
        rule.setContent {
            TEMPATHYTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) { OnboardingScreen(onComplete = {}) }
            }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.common_next)).assertIsDisplayed()
    }

    @Test fun pagesAreAnnouncedAndTheLastOneFinishes() {
        var done = 0
        rule.setContent { TEMPATHYTheme { OnboardingScreen(onComplete = { done++ }) } }
        val ctx = rule.activity
        rule.onNodeWithContentDescription(ctx.getString(R.string.onboarding_page_of, 1, 4)).assertExists()
        repeat(3) { rule.onNodeWithText(ctx.getString(R.string.common_next)).performClick() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(ctx.getString(R.string.onboarding_page_of, 4, 4)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.onboarding_get_started)).performClick()
        assertEquals(1, done)
    }

    @Test fun skipLeavesImmediately() {
        var done = 0
        rule.setContent { TEMPATHYTheme { OnboardingScreen(onComplete = { done++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.common_skip)).performClick()
        assertEquals(1, done)
    }
}
