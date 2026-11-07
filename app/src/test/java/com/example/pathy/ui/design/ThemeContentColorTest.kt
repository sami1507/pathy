package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.pathy.ui.theme.TEMPATHYTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Text that is not inside a Surface has no content color of its own and used to fall back to
 * black. On the dark theme that made labels such as "Remember me" almost invisible, so the theme
 * now provides a readable default in both modes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeContentColorTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun defaultTextContrast(dark: Boolean): Float {
        var content = Color.Unspecified
        var background = Color.Unspecified
        rule.setContent {
            TEMPATHYTheme(darkTheme = dark) {
                content = LocalContentColor.current
                background = MaterialTheme.colorScheme.background
            }
        }
        rule.waitForIdle()
        return contrast(content, background)
    }

    @Test fun defaultTextIsReadableOnTheDarkBackground() {
        val ratio = defaultTextContrast(dark = true)
        assertTrue("contrast was $ratio", ratio >= 4.5f)
    }

    @Test fun defaultTextIsReadableOnTheLightBackground() {
        val ratio = defaultTextContrast(dark = false)
        assertTrue("contrast was $ratio", ratio >= 4.5f)
    }
}
