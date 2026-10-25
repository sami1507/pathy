package com.example.pathy.ui.design

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.pathy.ui.theme.PathyColors

/**
 * Brand colors for every top app bar: deep forest green with light text in the light theme,
 * the raised night surface with the normal on-surface text in the dark theme. One place, so the
 * screens stop drifting apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun pathyTopBarColors(): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val container = if (isDark) scheme.surfaceContainer else PathyColors.AppBar
    val content = if (isDark) scheme.onSurface else Color.White
    return TopAppBarDefaults.topAppBarColors(
        containerColor = container,
        scrolledContainerColor = container,
        navigationIconContentColor = content,
        titleContentColor = content,
        actionIconContentColor = content
    )
}

/** Secondary text (subtitles) that sits on a [pathyTopBarColors] bar. */
@Composable
fun pathyTopBarSubtitleColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    return if (isDark) scheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f)
}
