package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.data.CommunityItem
import com.example.pathy.data.Post
import com.example.pathy.data.PostCardContent
import com.example.pathy.ui.screens.CommunityContent
import com.example.pathy.ui.screens.PublishState
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The community feed in each state it can be in: posts, first load, empty, failed, publishing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class CommunityScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun post(id: String, user: String, text: String) = Post(
        postId = id, userId = "u-$id", userName = user, userProfileImageUrl = "",
        trailId = "t1", trailName = "Wadi Qelt", postText = text, imageUrls = emptyList(),
        trailRating = 4.5f, timestamp = System.currentTimeMillis() - 3 * 3_600_000L, likesCount = 12, commentsCount = 3
    )

    private val feed = listOf(
        CommunityItem.PostItem(post("p1", "Layla Haddad", "The canyon was green after the rain. Bring water: there is almost no shade after the second bridge.")),
        CommunityItem.PostItem(post("p2", "Omar Nasser", "Sunrise from the ridge. Worth the early start."))
    )

    @Composable
    private fun community(
        items: List<CommunityItem> = feed,
        isLoading: Boolean = false,
        loadFailed: Boolean = false,
        publishState: PublishState = PublishState.Idle,
        onNewPost: () -> Unit = {},
        onNewEvent: () -> Unit = {},
        onRefresh: () -> Unit = {}
    ) = CommunityContent(
        items = items, isLoading = isLoading, loadFailed = loadFailed, publishState = publishState,
        publisherAvatar = null, onBack = {}, onNewPost = onNewPost, onNewEvent = onNewEvent, onRefresh = onRefresh
    ) { item ->
        if (item is CommunityItem.PostItem) {
            PostCardContent(
                post = item.post, timeText = "3h ago", isLiked = false, isOwner = false, likeScaled = false,
                lastComment = null, onProfileClick = {}, onTrailClick = {}, onLikeClick = {},
                onCommentsClick = {}, onDeleteClick = {}, onPhotoClick = {}
            )
        }
    }

    @Test fun feedEnglish() = rule.captureTo("community_feed_en", false) { community() }

    @Test fun feedDark() = rule.captureTo("community_feed_dark", true) { community() }

    @Test fun firstLoad() = rule.captureTo("community_loading", false) { community(items = emptyList(), isLoading = true) }

    @Test fun empty() = rule.captureTo("community_empty", false) { community(items = emptyList()) }

    @Test fun emptyDark() = rule.captureTo("community_empty_dark", true) { community(items = emptyList()) }

    @Test fun failed() = rule.captureTo("community_error", false) { community(items = emptyList(), loadFailed = true) }

    @Test fun publishing() = rule.captureTo("community_publishing", false) { community(publishState = PublishState.Publishing) }

    @Test fun published() = rule.captureTo("community_published", false) { community(publishState = PublishState.Done) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun feedArabic() = rule.captureTo("community_feed_ar", false) { community() }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun emptyArabic() = rule.captureTo("community_empty_ar", false) { community(items = emptyList()) }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun emptyHebrew() = rule.captureTo("community_empty_he", false) { community(items = emptyList()) }

    @Test fun newPostAndNewEventAreRealButtonsWithRoomForAThumb() {
        var post = 0
        var event = 0
        rule.setContent { TEMPATHYTheme { community(onNewPost = { post++ }, onNewEvent = { event++ }) } }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.community_new_post)).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(ctx.getString(R.string.community_new_event)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, post)
        assertEquals(1, event)
    }

    @Test fun anEmptyFeedOffersToWriteTheFirstPost() {
        var post = 0
        rule.setContent { TEMPATHYTheme { community(items = emptyList(), onNewPost = { post++ }) } }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.community_empty_title)).assertExists()
        // The header button and the empty state's button carry the same label.
        rule.onAllNodesWithText(ctx.getString(R.string.community_new_post)).onLast().performClick()
        assertEquals(1, post)
    }

    @Test fun aFailedLoadSaysSoAndRetries() {
        var refreshed = 0
        rule.setContent { TEMPATHYTheme { community(items = emptyList(), loadFailed = true, onRefresh = { refreshed++ }) } }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.community_error_title)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.community_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.retry)).performClick()
        assertEquals(1, refreshed)
    }

    @Test fun loadingShowsNeitherTheEmptyNorTheErrorMessage() {
        rule.setContent { TEMPATHYTheme { community(items = emptyList(), isLoading = true) } }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.common_loading)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.community_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.community_error_title)).assertDoesNotExist()
    }

    @Test fun postsStayOnScreenWhileRefreshing() {
        rule.setContent { TEMPATHYTheme { community(isLoading = true) } }
        rule.onNodeWithText("Layla Haddad").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.common_loading)).assertDoesNotExist()
    }
}
