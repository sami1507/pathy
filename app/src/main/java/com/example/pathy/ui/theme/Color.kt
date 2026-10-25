package com.example.pathy.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * PATHY nature palette. Every screen should use these tokens (via PathyColors or the
 * MaterialTheme color scheme), never raw hex values.
 *
 *  - Forest / Moss: brand greens (headers, primary actions, success)
 *  - Sage / Stone / Mist: soft neutrals for surfaces and borders
 *  - Sand / Bark / Clay: earth tones (highlights, trail types, warnings)
 *  - Sky: water and navigation
 *  - Ink: text colors, dark enough for WCAG AA contrast on Mist and white
 */

// Greens
val Forest950 = Color(0xFF0B2016)
val Forest800 = Color(0xFF14402A)
val Forest700 = Color(0xFF1B5E3B)
val Moss600 = Color(0xFF2F7A4C)
val Moss500 = Color(0xFF3F8F5E)
val Fern400 = Color(0xFF6DB38A)
val Sage300 = Color(0xFFA7C9B2)
val Sage200 = Color(0xFFCFE3D5)
val Sage100 = Color(0xFFE6F1E9)
val Mist50 = Color(0xFFF6FAF6)

// Neutral surfaces
val Stone100 = Color(0xFFEEF2EE)

// Earth tones
val Bark700 = Color(0xFF5A4030)
val Sand400 = Color(0xFFD9B77E)
val Sand100 = Color(0xFFF6EEDD)
val Clay600 = Color(0xFFB85C32)
val Clay300 = Color(0xFFE39A74)
val Clay100 = Color(0xFFF8E4DA)
val Amber500 = Color(0xFFD08C12)

// Water and navigation
val Sky600 = Color(0xFF2D7A8C)
val Sky700 = Color(0xFF1F5E6D)
val Sky300 = Color(0xFF7FC0CF)
val Sky100 = Color(0xFFDDF0F4)

// Community accent
val Berry600 = Color(0xFF8E3B63)

// Status
val Ember600 = Color(0xFFB3261E)
val Ember800 = Color(0xFF7F1D18)

// Text
val Ink900 = Color(0xFF14201A)
val Ink700 = Color(0xFF3A4A41)
val Ink500 = Color(0xFF5E6F65)
val Ink400 = Color(0xFF7D8E84)

// Dark theme
val NightBackground = Color(0xFF0C1510)
val NightSurface = Color(0xFF13211A)
val NightSurfaceVariant = Color(0xFF1C2E24)
val NightOutline = Color(0xFF2F4A3B)
val NightText = Color(0xFFE7F1EA)
val NightTextSecondary = Color(0xFFA9BDB0)

// Surface layers (Material 3 container roles). Cards and sheets use these by default, so they must
// be tinted with the palette; the Material baseline would otherwise show purple-grey surfaces.
val SurfaceDim = Color(0xFFDCE5DE)
val SurfaceLow = Color(0xFFF5F9F5)
val SurfaceMid = Color(0xFFF0F5F1)
val SurfaceHigh = Color(0xFFEBF1EC)
val SurfaceHighest = Color(0xFFE6EEE8)

val NightSurfaceLowest = Color(0xFF09110D)
val NightSurfaceMid = Color(0xFF17271E)
val NightSurfaceHighest = Color(0xFF233A2D)
val NightSurfaceBright = Color(0xFF28402F)
