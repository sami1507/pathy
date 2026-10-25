package com.example.pathy.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full Material 3 type scale for PATHY. Headings are tight and bold, body text is open for
 * readability on the map and in long lists. Every screen should use these styles, not raw sizes.
 *
 * The scale is tuned per script by [pathyTypography]:
 *  - Arabic: letter spacing is forced to 0 (extra tracking pulls cursive letters apart and breaks
 *    the joins), line height is taller for the stacked dots and marks, and the heaviest weight is
 *    capped at Bold because Arabic strokes already read darker than Latin.
 *  - Hebrew: no tracking and slightly taller lines; same weight cap.
 *  - Latin: the base scale.
 */
enum class TextScript { LATIN, HEBREW, ARABIC }

/** Maps a BCP-47 / ISO language code (including the legacy "iw" for Hebrew) to its script. */
fun scriptForLanguage(language: String?): TextScript = when (language?.lowercase()) {
    "ar" -> TextScript.ARABIC
    "he", "iw" -> TextScript.HEBREW
    else -> TextScript.LATIN
}

private class Spec(
    val weight: FontWeight,
    val size: Float,
    val lineHeight: Float,
    val tracking: Float
)

private val scale = mapOf(
    "displayLarge" to Spec(FontWeight.ExtraBold, 48f, 54f, -0.5f),
    "displayMedium" to Spec(FontWeight.ExtraBold, 40f, 46f, -0.4f),
    "displaySmall" to Spec(FontWeight.Bold, 32f, 38f, -0.3f),
    "headlineLarge" to Spec(FontWeight.Bold, 28f, 34f, -0.2f),
    "headlineMedium" to Spec(FontWeight.Bold, 24f, 30f, -0.1f),
    "headlineSmall" to Spec(FontWeight.SemiBold, 20f, 26f, 0f),
    "titleLarge" to Spec(FontWeight.SemiBold, 20f, 26f, 0f),
    "titleMedium" to Spec(FontWeight.SemiBold, 16f, 22f, 0.1f),
    "titleSmall" to Spec(FontWeight.SemiBold, 14f, 20f, 0.1f),
    "bodyLarge" to Spec(FontWeight.Normal, 16f, 24f, 0.2f),
    "bodyMedium" to Spec(FontWeight.Normal, 14f, 21f, 0.2f),
    "bodySmall" to Spec(FontWeight.Normal, 12f, 17f, 0.3f),
    "labelLarge" to Spec(FontWeight.SemiBold, 14f, 20f, 0.2f),
    "labelMedium" to Spec(FontWeight.SemiBold, 12f, 16f, 0.4f),
    "labelSmall" to Spec(FontWeight.Medium, 11f, 14f, 0.5f)
)

private fun style(name: String, script: TextScript): TextStyle {
    val s = scale.getValue(name)
    val weight = if (script != TextScript.LATIN && s.weight == FontWeight.ExtraBold) FontWeight.Bold else s.weight
    val lineHeightFactor = when (script) {
        TextScript.ARABIC -> 1.18f
        TextScript.HEBREW -> 1.06f
        TextScript.LATIN -> 1f
    }
    val tracking: TextUnit = if (script == TextScript.LATIN) s.tracking.sp else 0.sp
    return TextStyle(
        fontFamily = PathyFontFamily,
        fontWeight = weight,
        fontSize = s.size.sp,
        lineHeight = (s.lineHeight * lineHeightFactor).sp,
        letterSpacing = tracking
    )
}

fun pathyTypography(language: String? = null): Typography {
    val script = scriptForLanguage(language)
    return Typography(
        displayLarge = style("displayLarge", script),
        displayMedium = style("displayMedium", script),
        displaySmall = style("displaySmall", script),
        headlineLarge = style("headlineLarge", script),
        headlineMedium = style("headlineMedium", script),
        headlineSmall = style("headlineSmall", script),
        titleLarge = style("titleLarge", script),
        titleMedium = style("titleMedium", script),
        titleSmall = style("titleSmall", script),
        bodyLarge = style("bodyLarge", script),
        bodyMedium = style("bodyMedium", script),
        bodySmall = style("bodySmall", script),
        labelLarge = style("labelLarge", script),
        labelMedium = style("labelMedium", script),
        labelSmall = style("labelSmall", script)
    )
}

/** Latin scale, kept for code that needs a static reference. The theme uses [pathyTypography]. */
val Typography = pathyTypography()

/** Rounded, friendly shapes: cards 20-28 dp, chips and pills fully rounded. */
val PathyShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)
