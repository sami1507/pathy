package com.example.pathy.ui.design

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pathy.data.Comment
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.DisplayComment
import com.example.pathy.data.EnhancedTrailData
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.Post
import com.example.pathy.data.PostCardContent
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.TrailInfo
import com.example.pathy.data.TrailTags
import com.example.pathy.screens.CreatePostContent
import com.example.pathy.screens.PostGap
import com.example.pathy.screens.PremiumTrailCard
import com.example.pathy.screens.TrailChips
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the post composer, the post card and the trail card under each app language and at the largest
 * font size, and checks that every button is big enough to press. Only screens that need neither Firebase
 * nor Mapbox can be drawn on the JVM, which is why these take plain data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostAndTrailScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) =
        composeRule.captureTo(name, dark = false) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) { content() }
        }

    // ---- data ---------------------------------------------------------------------------------

    private val trail = SavedTrail(
        id = "t1", name = "Wadi Qelt", distanceInMeters = 9_400.0, difficulty = DifficultyLevel.MEDIUM, rating = 4.5f
    )

    private val longText = "We started early from the spring and the whole canyon was green after the rain. " +
        "The path is clear and well marked all the way, with a few steep steps near the monastery. " +
        "Bring water and a hat: there is almost no shade after the second bridge. Worth every step."

    private fun post(text: String = longText, photos: Int = 3) = Post(
        postId = "p1", userId = "u1", userName = "Layla Haddad", userProfileImageUrl = "",
        trailId = "t1", trailName = "Wadi Qelt", postText = text,
        imageUrls = List(photos) { "https://example.invalid/photo$it.jpg" },
        trailRating = 4.5f, timestamp = System.currentTimeMillis() - 3 * 3_600_000L, likesCount = 12, commentsCount = 3
    )

    private val comment = DisplayComment(Comment("c1", "u2", "Thanks for the tip about the shade!"), "Omar", "")

    private fun trailData(
        name: String = "Wadi Qelt",
        info: TrailInfo? = TrailInfo(waymark = "red", network = "lwn", warnings = listOf("SEASONAL", "OUTDATED"),
            updatedMs = System.currentTimeMillis() - 400L * 86_400_000L, quality = 8)
    ) = EnhancedTrailData(
        id = "osm_r1", name = name, description = "", trailType = "hiking", difficulty = DifficultyLevel.MEDIUM,
        distanceInMeters = 9_400.0, elevationGain = null, estimatedDuration = 3 * 3_600_000L, rating = 0f, reviewCount = 0,
        geoPoints = listOf(LatLngDTO(31.8, 35.3)), midpoint = LatLngDTO(31.8, 35.3), distanceFromUser = 3_200.0,
        locationName = "Judean Desert", features = emptyList(), photoUrls = emptyList(), surface = "ground", isLoop = false,
        tags = TrailTags(), info = info
    )

    // ---- composer -----------------------------------------------------------------------------

    @Composable
    private fun composer(withTrail: Boolean, text: String, photos: Int, gap: PostGap?, checking: Boolean = false) =
        CreatePostContent(
            authorName = "Layla Haddad", authorImage = null,
            trail = if (withTrail) trail else null,
            text = text, onTextChange = {},
            photos = List(photos) { Uri.parse("content://invalid/$it") },
            onAddPhoto = {}, onRemovePhoto = {}, gap = gap, checking = checking,
            onBack = {}, onChooseTrail = {}, onPublish = {}
        )

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun composerEmptyEnglish() = shot("post_composer_empty_en") { composer(false, "", 0, PostGap.TRAIL) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun composerFilledArabic() = shot("post_composer_filled_ar") { composer(true, "كانت الرحلة رائعة والمسار واضح.", 2, null) }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun composerFilledHebrew() = shot("post_composer_filled_he") { composer(true, "הטיול היה נהדר והשביל ברור.", 5, null) }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun composerChecking() = shot("post_composer_checking_en") { composer(true, "Great hike", 1, null, checking = true) }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun composerAtLargestFont() = shot("post_composer_font2_en", fontScale = 2f) { composer(true, "Great hike", 3, null) }

    // ---- post card ------------------------------------------------------------------------------

    @Composable
    private fun card(p: Post, comment: DisplayComment? = this.comment, owner: Boolean = true) = Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
    ) {
        PostCardContent(
            post = p, timeText = "3 h", isLiked = true, isOwner = owner, likeScaled = false, lastComment = comment,
            onProfileClick = {}, onTrailClick = {}, onLikeClick = {}, onCommentsClick = {}, onDeleteClick = {}, onPhotoClick = {}
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun postCardEnglish() = shot("post_card_en") { card(post()) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun postCardArabic() = shot("post_card_ar") {
        card(post("مررنا بالوادي صباحًا وكان كل شيء أخضر بعد المطر. المسار واضح ومعلَّم بالكامل مع بعض الدرجات الحادة قرب الدير. أحضروا ماءً وقبعة لأن الظل قليل بعد الجسر الثاني."))
    }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun postCardHebrew() = shot("post_card_he") {
        card(post("יצאנו מוקדם מהמעיין והנחל היה ירוק אחרי הגשם. השביל ברור ומסומן לכל אורכו. כדאי להביא מים וכובע כי כמעט אין צל אחרי הגשר השני."))
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun postCardAtLargestFont() = shot("post_card_font2_en", fontScale = 2f) { card(post()) }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun postCardWithoutPhotosOrComments() = shot("post_card_plain_en") {
        card(post("Short and sweet.", photos = 0).copy(commentsCount = 0, likesCount = 0), comment = null, owner = false)
    }

    // ---- trail card -----------------------------------------------------------------------------

    @Composable
    private fun trails() = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        PremiumTrailCard(trailData(), onClick = {})
        // A marked route with no name of its own is shown by its paint colour.
        PremiumTrailCard(trailData(name = "", info = TrailInfo(waymark = "blue", network = "rwn", quality = 9, updatedMs = System.currentTimeMillis() - 20L * 86_400_000L)), onClick = {})
        PremiumTrailCard(trailData(name = "Old forest path", info = null), onClick = {})
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun trailCardsEnglish() = shot("trail_cards_en") { trails() }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun trailCardsArabic() = shot("trail_cards_ar") { trails() }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun trailCardsHebrew() = shot("trail_cards_he") { trails() }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun trailChipsStandAlone() = shot("trail_chips_en") {
        Column(Modifier.padding(16.dp)) {
            TrailChips(TrailInfo(waymark = "green", network = "nwn", warnings = listOf("HARD_TO_FOLLOW", "NEEDS_CHECK"),
                updatedMs = System.currentTimeMillis() - 5L * 86_400_000L, verified = true))
        }
    }

    // ---- touch targets ---------------------------------------------------------------------------

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun everyPostCardButtonIsBigEnoughToPress() {
        composeRule.setContent {
            com.example.pathy.ui.theme.TEMPATHYTheme {
                card(post())
            }
        }
        composeRule.waitForIdle()
        for (label in listOf("Like, 12 likes", "Comments, 3", "Menu")) {
            assertTouchTargetAtLeast48(composeRule.onNodeWithContentDescription(label), label)
        }
        assertTouchTargetAtLeast48(composeRule.onNodeWithText("Comments (3)"), "Comments (3)")
    }

    /** The area that reacts to a finger (which may be larger than what is drawn) must be at least 48 x 48 dp. */
    private fun assertTouchTargetAtLeast48(node: androidx.compose.ui.test.SemanticsNodeInteraction, what: String) {
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        val w = bounds.width / density
        val h = bounds.height / density
        org.junit.Assert.assertTrue("$what is ${w}x${h} dp, smaller than 48 dp", w >= 47.5f && h >= 47.5f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
    fun theComposerButtonsAreBigEnoughToPress() {
        composeRule.setContent {
            com.example.pathy.ui.theme.TEMPATHYTheme {
                composer(true, "Great hike", 2, null)
            }
        }
        composeRule.waitForIdle()
        assertTouchTargetAtLeast48(composeRule.onNodeWithText("Publish post"), "Publish post")
        assertTouchTargetAtLeast48(composeRule.onNodeWithText("Change"), "Change")
        assertTouchTargetAtLeast48(composeRule.onNodeWithContentDescription("Remove photo 1"), "Remove photo 1")
    }
}
