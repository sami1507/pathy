package com.example.pathy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/** "#RRGGBB" string for Mapbox expressions and other string-based color APIs. */
fun Color.toHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

/** Light top, base and dark outline of a map pin, as Android color ints. */
data class PinTones(val top: Int, val bottom: Int, val stroke: Int)

/**
 * Colors drawn inside the map (lines, markers, labels). They come from the same nature tokens as
 * the rest of the UI, so the map and the interface stay one family. Mapbox needs strings or ints,
 * so each color is also available as `...Hex` or `...Int`.
 */
object MapPalette {

    // Route: dark green line with a white casing, readable on cream, satellite and dark styles
    private val routeLine = Forest700
    private val routeCasing = Color.White
    private val routeUpcoming = Sky600
    private val userPuck = Sky600

    // Where a trail starts and where it ends, drawn as dots on its line
    val startMarkerHex: String = Moss600.toHex()
    val endMarkerHex: String = Forest950.toHex()

    val routeLineHex: String = routeLine.toHex()
    val routeCasingHex: String = routeCasing.toHex()
    val routeUpcomingHex: String = routeUpcoming.toHex()
    val userPuckHex: String = userPuck.toHex()

    // Map labels: white text with a deep forest halo
    val labelTextHex: String = Color.White.toHex()
    val labelHaloHex: String = Forest950.toHex()

    // Trail type lines (same tokens as the trail type chips)
    val hiking: String = PathyColors.TrailHiking.toHex()
    val mtb: String = PathyColors.TrailMTB.toHex()
    val cycling: String = PathyColors.TrailCycling.toHex()
    val offroad: String = PathyColors.TrailOffroad.toHex()

    // Difficulty (same tokens as the difficulty tags)
    val easy: String = PathyColors.DifficultyEasy.toHex()
    val moderate: String = PathyColors.DifficultyModerate.toHex()
    val hard: String = PathyColors.DifficultyHard.toHex()
    val extreme: String = PathyColors.DifficultyExtreme.toHex()

    // Group member markers: distinct hues, all from the palette. No yellow: it vanishes on light maps.
    val memberColors: List<String> = listOf(
        Moss500, Sky600, Clay600, Berry600, Amber500, Sky300, Bark700, Fern400
    ).map { it.toHex() }
    val memberDefaultHex: String = Moss500.toHex()

    // Group map marker accents
    val markerPrimaryInt: Int = PathyColors.Primary.toArgb()
    val markerInfoInt: Int = PathyColors.Secondary.toArgb()
    val markerHighlightInt: Int = PathyColors.Highlight.toArgb()

    /** Three-tone pin derived from one base color, so the pin matches the line of its trail type. */
    fun pin(base: Color): PinTones = PinTones(
        top = lerp(base, Color.White, 0.35f).toArgb(),
        bottom = base.toArgb(),
        stroke = lerp(base, Color.Black, 0.35f).toArgb()
    )
}
