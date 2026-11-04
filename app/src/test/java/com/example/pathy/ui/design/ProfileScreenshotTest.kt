package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.screens.ProfileActions
import com.example.pathy.screens.ProfileContent
import com.example.pathy.screens.ProfileUiState
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The profile in its different situations: your own, someone else's, long names, uploading a photo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class ProfileScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun mine(
        name: String = "Ahmad Khalil",
        email: String = "ahmad@example.com",
        points: Int? = 1240,
        uploading: Boolean = false,
        hasPosts: Boolean = false
    ) = ProfileUiState(
        name = name, email = email, phone = "+972 50 123 4567", gender = "male", avatar = null,
        points = points, isCurrentUser = true, uploadingPhoto = uploading, hasPosts = hasPosts
    )

    private fun theirs() = ProfileUiState(
        name = "Layla Haddad", email = "layla@example.com", phone = "", gender = "female", avatar = null,
        points = 87, isCurrentUser = false, hasPosts = false
    )

    private val noPosts: LazyListScope.() -> Unit = {}

    private val twoPosts: LazyListScope.() -> Unit = {
        items(listOf("Sunrise on Mount Meron", "Group walk, Saturday 07:00")) { title ->
            NatureCard { Text(title) }
        }
    }

    @Composable
    private fun profile(state: ProfileUiState, actions: ProfileActions = ProfileActions(), posts: LazyListScope.() -> Unit = noPosts) =
        ProfileContent(state, actions, posts)

    @Test fun mineEnglish() = rule.captureTo("profile_mine_en", false) { profile(mine(hasPosts = true), posts = twoPosts) }

    @Test fun mineDark() = rule.captureTo("profile_mine_dark", true) { profile(mine(hasPosts = true), posts = twoPosts) }

    @Test fun mineEmpty() = rule.captureTo("profile_mine_empty", false) { profile(mine()) }

    @Test fun mineUploadingNoPoints() = rule.captureTo("profile_mine_uploading", false) { profile(mine(points = null, uploading = true)) }

    @Test fun longNameAndEmail() = rule.captureTo("profile_long_name", false) {
        profile(mine(name = "Abdelrahman Mohammed Al-Hassan Ibn Khaled Al-Shami", email = "abdelrahman.mohammed.alhassan.alshami@very-long-domain-example.com"))
    }

    @Test fun someoneElse() = rule.captureTo("profile_other_en", false) { profile(theirs()) }

    @Test fun fontScaleTwo() = rule.captureTo("profile_mine_font2", false) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
        ) { profile(mine()) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun mineArabic() = rule.captureTo("profile_mine_ar", false) { profile(mine(name = "أحمد خليل"), posts = twoPosts) }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h900dp-xxhdpi")
    fun mineHebrew() = rule.captureTo("profile_mine_he", false) { profile(mine(name = "אחמד חליל"), posts = twoPosts) }

    @Test fun yourOwnProfileOffersTheShortcutsAndEditing() {
        val clicks = mutableListOf<String>()
        val actions = ProfileActions(
            onEdit = { clicks += "edit" }, onChangePhoto = { clicks += "photo" }, onRecord = { clicks += "record" },
            onSavedTrails = { clicks += "saved" }, onOfflineTrails = { clicks += "offline" },
            onHistory = { clicks += "history" }, onBookmarks = { clicks += "bookmarks" }, onSos = { clicks += "sos" }
        )
        rule.setContent { TEMPATHYTheme { profile(mine(), actions) } }
        val ctx = rule.activity
        rule.onNodeWithContentDescription(ctx.getString(R.string.profile_edit_desc)).performClick()
        rule.onNodeWithContentDescription(ctx.getString(R.string.profile_change_photo)).performClick()
        listOf(
            R.string.profile_record_activity_button, R.string.profile_saved_trails_button,
            R.string.profile_offline_trails_button, R.string.profile_activity_history_button,
            R.string.profile_bookmarks_button, R.string.profile_sos_button
        ).forEach { id ->
            // The label sits inside the clickable card, so the merged node carries both text and click.
            rule.onNodeWithText(ctx.getString(id)).performScrollTo().assertHeightIsAtLeast(44.dp).performClick()
        }
        assertEquals(listOf("edit", "photo", "record", "saved", "offline", "history", "bookmarks", "sos"), clicks)
    }

    @Test fun someoneElsesProfileHasNoEditingOrShortcuts() {
        rule.setContent { TEMPATHYTheme { profile(theirs()) } }
        val ctx = rule.activity
        rule.onNodeWithContentDescription(ctx.getString(R.string.profile_edit_desc)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.profile_record_activity_button)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.profile_sos_button)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.profile_posts_empty_other)).assertExists()
    }

    @Test fun uploadingBlocksSecondPhotoPick() {
        var picked = 0
        rule.setContent { TEMPATHYTheme { profile(mine(uploading = true), ProfileActions(onChangePhoto = { picked++ })) } }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.profile_change_photo)).performClick()
        assertEquals(0, picked)
    }
}
