package com.example.pathy.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * PATHY's single theme. The brand palette is fixed on purpose: dynamic (wallpaper-based) color
 * was replacing the greens on Android 12+, so it is off by default.
 */
private val LightColorScheme = lightColorScheme(
    primary = Moss600,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Sage100,
    onPrimaryContainer = Forest700,

    secondary = Sky600,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = Sky100,
    onSecondaryContainer = Sky700,

    tertiary = Clay600,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = Clay100,
    onTertiaryContainer = Bark700,

    background = Mist50,
    onBackground = Ink900,

    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink900,
    surfaceVariant = Stone100,
    onSurfaceVariant = Ink500,
    surfaceTint = Moss600,
    surfaceBright = androidx.compose.ui.graphics.Color.White,
    surfaceDim = SurfaceDim,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    inverseSurface = Forest800,
    inverseOnSurface = Sage100,
    inversePrimary = Fern400,
    scrim = Forest950,

    outline = Sage300,
    outlineVariant = Sage200,

    error = Ember600,
    onError = androidx.compose.ui.graphics.Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Fern400,
    onPrimary = Forest950,
    primaryContainer = Forest700,
    onPrimaryContainer = Sage100,

    secondary = Sky300,
    onSecondary = Forest950,
    secondaryContainer = Sky700,
    onSecondaryContainer = Sky100,

    tertiary = Clay300,
    onTertiary = Forest950,
    tertiaryContainer = Bark700,
    onTertiaryContainer = Sand100,

    background = NightBackground,
    onBackground = NightText,

    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightTextSecondary,
    surfaceTint = Fern400,
    surfaceBright = NightSurfaceBright,
    surfaceDim = NightBackground,
    surfaceContainerLowest = NightSurfaceLowest,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurfaceMid,
    surfaceContainerHigh = NightSurfaceVariant,
    surfaceContainerHighest = NightSurfaceHighest,
    inverseSurface = NightText,
    inverseOnSurface = Forest950,
    inversePrimary = Moss600,
    scrim = Forest950,

    outline = NightOutline,
    outlineVariant = NightOutline,

    error = Clay300,
    onError = Forest950
)

@Composable
fun TEMPATHYTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // The type scale depends on the script of the active language (see pathyTypography).
    val language = LocalConfiguration.current.locales[0]?.language
    val typography = remember(language) { pathyTypography(language) }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = PathyShapes
    ) {
        // Text outside a Surface otherwise falls back to black, which disappears on the dark
        // background (the "Remember me" label on the sign-in screen did exactly that).
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
    }
}

/**
 * The way every activity should set its content. A screen that calls the plain `setContent` runs on
 * Material's default purple theme and system font instead of PATHY's, which is how thirteen screens
 * (the home map, profile, search, sign-up and others) went out looking off-brand.
 */
fun ComponentActivity.setPathyContent(content: @Composable () -> Unit) {
    setContent { TEMPATHYTheme(content = content) }
}
