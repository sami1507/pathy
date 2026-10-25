package com.example.pathy.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PATHY design tokens.
 *
 * [PathyColors] maps each semantic role to the nature palette in Color.kt. Screens keep using the
 * same names, so changing a token here updates the whole app.
 */

object PathyColors {
    // Brand greens
    val Primary = Moss600
    val PrimaryDark = Forest700
    val PrimaryLight = Fern400
    val PrimaryContainer = Sage100

    // Water and navigation
    val Secondary = Sky600
    val SecondaryDark = Sky700
    val SecondaryLight = Sky300
    val SecondaryContainer = Sky100

    // Earth accents
    val Accent = Clay600
    val AccentLight = Clay300
    val Highlight = Sand400
    val Bark = Bark700
    val Berry = Berry600
    val Night = Forest950
    val SandContainer = Sand100
    val AccentContainer = Clay100

    // Neutrals
    val Background = Mist50
    val Surface = Color.White
    val SurfaceVariant = Stone100
    val Outline = Sage300
    val OutlineVariant = Sage200

    // Text
    val TextPrimary = Ink900
    val TextSecondary = Ink500
    val TextTertiary = Ink400

    // Status
    val Success = Moss500
    val Warning = Amber500
    val Error = Ember600
    val Info = Sky600

    // Trail difficulty
    val DifficultyEasy = Moss500
    val DifficultyModerate = Amber500
    val DifficultyHard = Clay600
    val DifficultyExtreme = Ember800

    // Trail types
    val TrailHiking = Moss500
    val TrailMTB = Clay600
    val TrailCycling = Sky600
    val TrailOffroad = Bark700

    // App chrome
    val AppBar = Forest800
    val Title = Forest700
    val CardBorder = Sage200
    val ProgressBar = Sage300

    // Drawer menu
    val MenuSearchColor = Moss500
    val MenuCommunityColor = Sky700
    val MenuProfileColor = Sand400
    val MenuMapColor = Forest700
    val MenuSettingsColor = Bark700

    // Overlays: translucent forest tint over photos and maps (never pure black)
    val Scrim = Forest950.copy(alpha = 0.55f)
    val ScrimSoft = Forest950.copy(alpha = 0.20f)
}

// ============================================
// 🎬 ANIMATIONS
// ============================================

object PathyAnimations {
    // Duration constants
    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 500

    // Easing
    val FastOutSlowIn = FastOutSlowInEasing
    val LinearOutSlowIn = LinearOutSlowInEasing

    // Common animations
    fun <T> springAnimation(
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float = Spring.StiffnessLow
    ) = spring<T>(dampingRatio = dampingRatio, stiffness = stiffness)

    fun fadeIn() = fadeIn(animationSpec = tween(MEDIUM, easing = LinearOutSlowIn))
    fun fadeOut() = androidx.compose.animation.fadeOut(animationSpec = tween(FAST, easing = FastOutSlowIn))

    fun slideInFromBottom() = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(MEDIUM, easing = LinearOutSlowIn)
    )

    fun slideOutToBottom() = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(FAST, easing = FastOutSlowIn)
    )
}

// ============================================
// 📐 DIMENSIONS & SPACING
// ============================================

object PathyDimensions {
    // Spacing scale (4 dp grid)
    val SpaceXS = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 16.dp
    val SpaceL = 24.dp
    val SpaceXL = 32.dp
    val SpaceXXL = 48.dp

    // Border radius
    val RadiusXS = 4.dp
    val RadiusS = 8.dp
    val RadiusM = 12.dp
    val RadiusL = 16.dp
    val RadiusXL = 24.dp
    val RadiusFull = 999.dp

    // Elevation
    val ElevationNone = 0.dp
    val ElevationXS = 2.dp
    val ElevationS = 4.dp
    val ElevationM = 8.dp
    val ElevationL = 12.dp
    val ElevationXL = 16.dp

    // Component sizes
    val ButtonHeight = 56.dp
    val ButtonHeightSmall = 40.dp
    val IconSizeSmall = 20.dp
    val IconSizeMedium = 24.dp
    val IconSizeLarge = 32.dp
    val IconSizeXL = 48.dp

    // Divider
    val DividerThickness = 1.dp
}
