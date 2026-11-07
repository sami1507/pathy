package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailInfo
import com.example.pathy.data.TrailTags
import com.example.pathy.offline.OfflineTrails
import com.example.pathy.screens.OfflineSave
import com.example.pathy.screens.SavedTrailsContent
import com.example.pathy.screens.TrailDetailsActions
import com.example.pathy.screens.TrailDetailsContent
import com.example.pathy.screens.TrailListCard
import com.example.pathy.screens.toCardModel
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/** The trail list card, the trail page and the saved-on-this-phone list, in every state a hiker meets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class TrailScreensScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A winding line, so the sketch has a real shape to draw. */
    private fun windingPath(count: Int = 80, loop: Boolean = false): List<LatLngDTO> = List(count) { i ->
        val t = i / (count - 1.0)
        val angle = t * Math.PI * (if (loop) 2.0 else 3.2)
        LatLngDTO(31.80 + 0.03 * t * (if (loop) 0.0 else 1.0) + 0.012 * sin(angle), 35.40 + 0.05 * t * (if (loop) 0.0 else 1.0) + 0.016 * cos(angle))
    }

    private fun trail(
        id: String = "relation_1",
        name: String = "Wadi Qelt",
        difficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
        info: TrailInfo? = TrailInfo(waymark = "red", warnings = listOf("SEASONAL"), quality = 7),
        gain: Int? = 310,
        loop: Boolean = false
    ) = TrailData(
        id = id,
        name = name,
        geoPoints = windingPath(loop = loop),
        midpoint = LatLngDTO(31.81, 35.42),
        isHikingTrail = true,
        distanceInMeters = 8400.0,
        durationInMillis = 150 * 60_000L,
        difficulty = difficulty,
        locationName = "Judean Desert",
        tags = TrailTags(elevationGain = gain, maxElevation = gain?.plus(110)),
        info = info
    )

    private fun saved(t: TrailData) = OfflineTrails.Saved(t, savedAtMs = 1_700_000_000_000L, sizeBytes = 4_000L)

    private val noMap: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
    }
    private val noStatus: @Composable () -> Unit = {}

    @Composable
    private fun details(
        offline: OfflineSave = OfflineSave.NotSaved,
        online: Boolean = true,
        bookmarked: Boolean = false,
        t: TrailData = trail(),
        actions: TrailDetailsActions = TrailDetailsActions()
    ) = TrailDetailsContent(
        trail = t, title = t.name, gainText = "310 m", maxText = "420 m", elevationLoading = false,
        offlineSave = offline, online = online, bookmarked = bookmarked, actions = actions, map = noMap, status = noStatus
    )

    // ---- the card -------------------------------------------------------------------------------

    @Composable
    private fun cards() {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TrailListCard(trail().toCardModel(awayMeters = 3200.0), onClick = {})
            TrailListCard(trail("2", "Mount Meron summit loop through the Safed forest and the old stone terraces", DifficultyLevel.HARD, loop = true).toCardModel(), onClick = {}, offline = true)
            TrailListCard(trail("3", "Ein Gedi", DifficultyLevel.EASY, info = null, gain = null).toCardModel(awayMeters = 120_000.0), onClick = {})
            TrailListCard(trail("4", "", DifficultyLevel.EXTREME, TrailInfo(waymark = "blue")).toCardModel(), onClick = {})
        }
    }

    @Test fun cardsLight() = rule.captureTo("trails_cards_en", false) { cards() }

    @Test fun cardsDark() = rule.captureTo("trails_cards_dark", true) { cards() }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun cardsArabic() = rule.captureTo("trails_cards_ar", false) { cards() }

    @Test fun cardsWithLargeText() {
        RuntimeEnvironment.setFontScale(1.6f)
        rule.captureTo("trails_cards_largefont", false) { cards() }
    }

    @Test fun aCardIsOneButtonThatReadsAsASentence() {
        var clicked = 0
        rule.setContent { TEMPATHYTheme { TrailListCard(trail().toCardModel(awayMeters = 3200.0), onClick = { clicked++ }, offline = true) } }
        val ctx = rule.activity
        val summary = rule.onAllNodesWithContentDescription("Wadi Qelt", substring = true).onFirst()
        summary.assertExists()
        summary.assertContentDescriptionContains(ctx.getString(R.string.trail_tag_offline), substring = true)
        summary.assertContentDescriptionContains("8.4", substring = true)
        summary.performClick()
        assertEquals(1, clicked)
    }

    // ---- the trail page -------------------------------------------------------------------------

    @Test fun detailsNotSaved() = rule.captureTo("trail_details_en", false) { details() }

    @Test fun detailsSaving() = rule.captureTo("trail_details_saving", false) { details(OfflineSave.Saving(0.42f)) }

    @Test fun detailsSaved() = rule.captureTo("trail_details_saved", false) { details(OfflineSave.Saved("38 MB")) }

    @Test fun detailsSavedDark() = rule.captureTo("trail_details_saved_dark", true) { details(OfflineSave.Saved("38 MB"), bookmarked = true) }

    @Test fun detailsOfflineAndSaved() = rule.captureTo("trail_details_offline_saved", false) { details(OfflineSave.Saved("38 MB"), online = false) }

    @Test fun detailsOfflineNotSaved() = rule.captureTo("trail_details_offline_notsaved", false) { details(online = false) }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun detailsArabic() = rule.captureTo("trail_details_ar", false) { details(OfflineSave.Saved("38 MB")) }

    @Test fun startSaveAndPreviewAreWiredAndBigEnough() {
        val calls = mutableListOf<String>()
        val actions = TrailDetailsActions(
            onStart = { calls += "start" }, onPreview = { calls += "preview" }, onSaveOffline = { calls += "save" },
            onBookmark = { calls += "bookmark" }, onShare = { calls += "share" }, onAddToMine = { calls += "mine" }
        )
        rule.setContent { TEMPATHYTheme { details(actions = actions) } }
        val ctx = rule.activity
        fun tall(text: String, index: Int = 0) {
            val node = rule.onAllNodes(hasText(text)).get(index)
            val h = with(rule.density) { node.fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
            assertTrue("$text touch height was $h", h >= 47.5.dp)
        }
        tall(ctx.getString(R.string.trail_action_start))
        rule.onNodeWithText(ctx.getString(R.string.trail_action_start)).performClick()
        rule.onAllNodes(hasText(ctx.getString(R.string.trail_offline_save))).get(1).performScrollTo()
        tall(ctx.getString(R.string.trail_offline_save), index = 1)
        rule.onAllNodes(hasText(ctx.getString(R.string.trail_offline_save))).get(1).performClick()
        rule.onNodeWithText(ctx.getString(R.string.bookmark_add_button)).performScrollTo().performClick()
        rule.onNodeWithText(ctx.getString(R.string.trail_action_share)).performScrollTo().performClick()
        rule.onNodeWithText(ctx.getString(R.string.trail_save_button)).performScrollTo().performClick()
        assertEquals(listOf("start", "save", "bookmark", "share", "mine"), calls)
    }

    @Test fun withoutAConnectionNothingOffersToDownloadOrShare() {
        var saved = 0
        var shared = 0
        rule.setContent {
            TEMPATHYTheme { details(online = false, actions = TrailDetailsActions(onSaveOffline = { saved++ }, onShare = { shared++ })) }
        }
        val ctx = rule.activity
        // The save button is not offered; the card says why instead.
        rule.onAllNodes(hasText(ctx.getString(R.string.trail_offline_save))).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        rule.onNodeWithText(ctx.getString(R.string.trail_offline_needs_connection)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.trail_action_share)).performScrollTo().performClick()
        assertEquals(0, shared)
        assertEquals(0, saved)
        rule.onNodeWithText(ctx.getString(R.string.offline_banner_title)).assertExists()
    }

    @Test fun aSavedTrailCanBeRemovedAndShowsItsSize() {
        var removed = 0
        rule.setContent {
            TEMPATHYTheme { details(OfflineSave.Saved("38 MB"), actions = TrailDetailsActions(onRemoveOffline = { removed++ })) }
        }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.trail_offline_saved_title)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.trail_offline_saved_detail, "38 MB")).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.trail_offline_remove)).performScrollTo().performClick()
        assertEquals(1, removed)
    }

    @Test fun anUnknownSizeIsNotInventedAsZero() {
        rule.setContent { TEMPATHYTheme { details(OfflineSave.Saved(null)) } }
        rule.onNodeWithText(rule.activity.getString(R.string.trail_offline_saved_detail_nosize)).assertExists()
    }

    // ---- the saved list -------------------------------------------------------------------------

    @Composable
    private fun savedList(items: List<OfflineTrails.Saved>?, online: Boolean = true, onRemove: (TrailData) -> Unit = {}) =
        SavedTrailsContent(items = items, online = online, onBack = {}, onOpen = {}, onRemove = onRemove, onImport = {})

    private val three = listOf(
        saved(trail("a", "Wadi Qelt")),
        saved(trail("b", "Mount Meron summit loop through the Safed forest and the old stone terraces", DifficultyLevel.HARD, loop = true)),
        saved(trail("c", "Ein Gedi", DifficultyLevel.EASY, info = null))
    )

    @Test fun savedList() = rule.captureTo("saved_list_en", false) { savedList(three) }

    @Test fun savedListOffline() = rule.captureTo("saved_list_offline", false) { savedList(three, online = false) }

    @Test fun savedListDark() = rule.captureTo("saved_list_dark", true) { savedList(three) }

    @Test fun savedEmpty() = rule.captureTo("saved_empty", false) { savedList(emptyList()) }

    @Test fun savedEmptyOffline() = rule.captureTo("saved_empty_offline", false) { savedList(emptyList(), online = false) }

    @Test fun savedLoading() = rule.captureTo("saved_loading", false) { savedList(null) }

    @Test
    @Config(sdk = [34], qualifiers = "he-w412dp-h900dp-xxhdpi")
    fun savedHebrew() = rule.captureTo("saved_list_he", false) { savedList(three) }

    @Test fun eachSavedTrailHasANamedRemoveButtonThatIsBigEnough() {
        var removed: String? = null
        rule.setContent { TEMPATHYTheme { savedList(three, onRemove = { removed = it.id }) } }
        val ctx = rule.activity
        val description = ctx.getString(R.string.saved_trail_remove_desc, "Wadi Qelt")
        val button = rule.onNodeWithContentDescription(description)
        val h = with(rule.density) { button.fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
        assertTrue("remove button height was $h", h >= 47.5.dp)
        button.performClick()
        assertEquals("a", removed)
    }

    @Test fun anEmptyListExplainsHowToSaveATrail() {
        rule.setContent { TEMPATHYTheme { savedList(emptyList()) } }
        val ctx = rule.activity
        rule.onNodeWithText(ctx.getString(R.string.saved_trails_empty_title)).assertExists()
        rule.onNodeWithText(ctx.getString(R.string.saved_trails_empty_message)).assertExists()
    }
}
