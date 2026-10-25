package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.pathy.R
import com.example.pathy.screens.UltraModernHomeScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp")
class HomeStateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun failureDoesNotMasqueradeAsZeroActivitiesAndCanRetry() {
        var retries = 0
        rule.setContent { TEMPATHYTheme { UltraModernHomeScreen(statsError = true, onRetry = { retries++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.load_failed)).assertExists()
        rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.home_activity_count, 0, 0)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.retry)).performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun emptyAccountHasNoFabricatedTrailsAndTrackingWorks() {
        var starts = 0
        rule.setContent { TEMPATHYTheme { UltraModernHomeScreen(onStartTracking = { starts++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.home_empty)).assertExists()
        rule.onNodeWithText("Mountain Peak Trail").assertDoesNotExist()
        rule.onNodeWithText("8/10 hikes").assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.home_track)).performClick()
        rule.runOnIdle { assertEquals(1, starts) }
    }
}
