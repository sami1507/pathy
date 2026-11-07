package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.data.DrawerMenuItem
import com.example.pathy.data.DrawerSheet
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The side menu of the home map: every entry carries a visible name, not just an icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w412dp-h900dp-xxhdpi")
class DrawerScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun drawer(clicked: MutableList<String> = mutableListOf()) {
        val search = stringResource(R.string.common_search)
        val community = stringResource(R.string.community_title)
        val profile = stringResource(R.string.home_profile)
        val map = stringResource(R.string.menu_map)
        val settings = stringResource(R.string.settings)
        Box(Modifier.fillMaxSize()) {
            DrawerSheet(
                listOf(
                    DrawerMenuItem(search, Icons.Filled.Search) { clicked += search },
                    DrawerMenuItem(community, Icons.Filled.Forum) { clicked += community },
                    DrawerMenuItem(profile, Icons.Filled.AccountCircle) { clicked += profile },
                    DrawerMenuItem(map, Icons.Filled.Public) { clicked += map },
                    DrawerMenuItem(settings, Icons.Filled.Settings) { clicked += settings }
                )
            )
        }
    }

    @Test fun lightEnglish() = rule.captureTo("drawer_en", false) { drawer() }

    @Test fun darkEnglish() = rule.captureTo("drawer_dark", true) { drawer() }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h900dp-xxhdpi")
    fun arabic() = rule.captureTo("drawer_ar", false) { drawer() }

    @Test fun everyEntryShowsItsNameAndIsAtLeastAThumbTall() {
        val clicked = mutableListOf<String>()
        rule.setContent { TEMPATHYTheme { drawer(clicked) } }
        val ctx = rule.activity
        val labels = listOf(
            R.string.common_search, R.string.community_title, R.string.home_profile,
            R.string.menu_map, R.string.settings
        ).map(ctx::getString)
        labels.forEach { label ->
            val node = rule.onNodeWithText(label)
            node.assertExists()
            val height = with(rule.density) { node.fetchSemanticsNode().touchBoundsInRoot.height.toDp() }
            assertTrue("$label touch height was $height", height >= 47.5.dp)
            node.performClick()
        }
        assertEquals(labels, clicked)
    }
}
