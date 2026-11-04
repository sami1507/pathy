package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.bookmarks.Bookmark
import com.example.pathy.screens.BookmarksScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Saved trails: the list, the first load, nothing saved yet, and a failed load. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class BookmarksScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val hour = 3_600_000L

    private val saved = listOf(
        Bookmark("t1", "Wadi Qelt", System.currentTimeMillis() - 3 * hour, emptyMap()),
        Bookmark("t2", "Mount Meron summit loop through the Safed forest and the old stone terraces", System.currentTimeMillis() - 30 * hour, emptyMap()),
        Bookmark("t3", "Ein Gedi", System.currentTimeMillis() - 200 * hour, emptyMap())
    )

    @Composable
    private fun bookmarks(
        list: List<Bookmark> = saved,
        isLoading: Boolean = false,
        loadFailed: Boolean = false,
        onOpen: (String) -> Unit = {},
        onRemove: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onDiscover: () -> Unit = {}
    ) = BookmarksScreen(
        bookmarks = list, onBookmarkClick = onOpen, onRemoveBookmark = onRemove, onBack = {},
        isLoading = isLoading, loadFailed = loadFailed, onRetry = onRetry, onDiscover = onDiscover
    )

    @Test fun listEnglish() = rule.captureTo("bookmarks_list_en", false) { bookmarks() }

    @Test fun listDark() = rule.captureTo("bookmarks_list_dark", true) { bookmarks() }

    @Test fun loading() = rule.captureTo("bookmarks_loading", false) { bookmarks(list = emptyList(), isLoading = true) }

    @Test fun empty() = rule.captureTo("bookmarks_empty", false) { bookmarks(list = emptyList()) }

    @Test fun failed() = rule.captureTo("bookmarks_error", false) { bookmarks(list = emptyList(), loadFailed = true) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun listArabic() = rule.captureTo("bookmarks_list_ar", false) { bookmarks() }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun emptyHebrew() = rule.captureTo("bookmarks_empty_he", false) { bookmarks(list = emptyList()) }

    @Test fun openingAndRemovingReportTheTrail() {
        var opened: String? = null
        var removed: String? = null
        rule.setContent { TEMPATHYTheme { bookmarks(onOpen = { opened = it }, onRemove = { removed = it }) } }
        val ctx = rule.activity
        rule.onNodeWithText("Wadi Qelt").performClick()
        assertEquals("t1", opened)
        val remove = rule.onAllNodesWithContentDescription(ctx.getString(R.string.bookmarks_remove)).onFirst()
        // The icon is drawn smaller than a thumb, but the area that reacts to touch must be at least 48 dp.
        val touchHeight = with(rule.density) { remove.fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
        assertTrue("touch height was $touchHeight", touchHeight >= 47.5.dp)
        remove.performClick()
        assertEquals("t1", removed)
    }

    @Test fun anEmptyListPointsToDiscovery() {
        var discover = 0
        rule.setContent { TEMPATHYTheme { bookmarks(list = emptyList(), onDiscover = { discover++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.home_discover)).performClick()
        assertEquals(1, discover)
    }

    @Test fun aFailedLoadCanBeRetriedAndIsNotCalledEmpty() {
        var retried = 0
        rule.setContent { TEMPATHYTheme { bookmarks(list = emptyList(), loadFailed = true, onRetry = { retried++ }) } }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.bookmarks_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.retry)).performClick()
        assertEquals(1, retried)
    }

    @Test fun theFirstLoadIsNotCalledEmpty() {
        rule.setContent { TEMPATHYTheme { bookmarks(list = emptyList(), isLoading = true) } }
        rule.onNodeWithText(rule.activity.getString(R.string.bookmarks_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.common_loading)).assertExists()
    }
}
