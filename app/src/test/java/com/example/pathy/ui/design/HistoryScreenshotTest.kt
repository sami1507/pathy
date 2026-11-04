package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.example.pathy.R
import com.example.pathy.history.ActivitySummary
import com.example.pathy.screens.HistoryContent
import com.example.pathy.screens.HistoryUiState
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The activity history list in each state: filled, filtered to nothing, first load, empty, failed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class HistoryScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val day = 86_400_000L

    private fun outing(id: String, type: String, daysAgo: Int, km: Float, minutes: Int, climb: Float, pending: Boolean = false) =
        ActivitySummary(
            id = id, activityType = type, startTime = 1_792_000_000_000L - daysAgo * day,
            duration = minutes * 60_000L, distance = km * 1000f, elevationGain = climb, calories = 400,
            photos = emptyList(), notes = "", pendingUpload = pending
        )

    private val outings = listOf(
        outing("a1", "HIKING", 0, 9.4f, 187, 412f, pending = true),
        outing("a2", "RUNNING", 2, 5.1f, 31, 38f),
        outing("a3", "CYCLING", 5, 42.7f, 128, 540f),
        outing("a4", "MOUNTAIN_BIKING", 9, 18.2f, 96, 610f),
        outing("a5", "HIKING", 14, 12.6f, 260, 795f)
    )

    @Composable
    private fun history(
        state: HistoryUiState = HistoryUiState.Content(outings),
        filter: String = "All",
        onFilter: (String) -> Unit = {},
        onOpen: (String) -> Unit = {},
        onRecord: () -> Unit = {},
        onRetry: () -> Unit = {}
    ) = HistoryContent(state, filter, onFilter, onOpen, onBack = {}, onRecord = onRecord, onRetry = onRetry)

    @Test fun listEnglish() = rule.captureTo("history_list_en", false) { history() }

    @Test fun listDark() = rule.captureTo("history_list_dark", true) { history() }

    @Test fun listLargeFont() = rule.captureTo("history_list_font2", false) {
        val base = LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) { history() }
    }

    @Test fun filteredToRunning() = rule.captureTo("history_filter_running", false) { history(filter = "Running") }

    @Test fun filterWithNothing() = rule.captureTo("history_filter_none", false) {
        history(state = HistoryUiState.Content(outings.filter { it.activityType == "HIKING" }), filter = "Cycling")
    }

    @Test fun loading() = rule.captureTo("history_loading", false) { history(state = HistoryUiState.Loading) }

    @Test fun empty() = rule.captureTo("history_empty", false) { history(state = HistoryUiState.Empty) }

    @Test fun failed() = rule.captureTo("history_error", false) { history(state = HistoryUiState.Error) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun listArabic() = rule.captureTo("history_list_ar", false) { history() }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun listHebrew() = rule.captureTo("history_list_he", false) { history() }

    @Test fun openingAnOutingReportsItsId() {
        var opened: String? = null
        rule.setContent { TEMPATHYTheme { history(onOpen = { opened = it }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.activity_pending_upload)).performClick()
        assertEquals("a1", opened)
    }

    @Test fun anEmptyHistoryOffersToRecordTheFirstOuting() {
        var recorded = 0
        rule.setContent { TEMPATHYTheme { history(state = HistoryUiState.Empty, onRecord = { recorded++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.profile_record_activity_button)).performClick()
        assertEquals(1, recorded)
    }

    @Test fun aFailedLoadCanBeRetried() {
        var retried = 0
        rule.setContent { TEMPATHYTheme { history(state = HistoryUiState.Error, onRetry = { retried++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.retry)).performClick()
        assertEquals(1, retried)
    }

    @Test fun anEmptyFilterCanBeCleared() {
        var chosen: String? = null
        rule.setContent {
            TEMPATHYTheme {
                history(state = HistoryUiState.Content(outings.filter { it.activityType == "HIKING" }), filter = "Cycling", onFilter = { chosen = it })
            }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.history_empty_filter)).assertExists()
        // The "All" chip and the button under the message share a label; the button is the last one on screen.
        rule.onAllNodesWithText(rule.activity.getString(R.string.filter_all)).onLast().performClick()
        assertEquals("All", chosen)
    }
}
