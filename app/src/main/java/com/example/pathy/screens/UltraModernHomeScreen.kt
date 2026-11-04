package com.example.pathy.screens

import com.example.pathy.i18n.isolateBidi
import androidx.compose.ui.res.pluralStringResource
import com.example.pathy.ui.theme.PathyColors

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.ui.components.*
import com.example.pathy.ui.design.MountainRange
import com.example.pathy.ui.design.NatureActionTile
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NaturePrimaryAction
import com.example.pathy.ui.design.NatureTag
import com.example.pathy.ui.design.TopoBackground
import com.example.pathy.ui.theme.Forest800
import com.example.pathy.ui.theme.Moss600
import java.util.*
import androidx.compose.ui.res.stringResource
import com.example.pathy.R

/**
 * Home: a forest hero with the greeting and a profile button, the user's totals on a card that
 * overlaps the hero, one big "record" action, the weather, and four shortcuts.
 */

@Composable
fun UltraModernHomeScreen(
    userName: String? = null,
    totalDistance: Float = 0f,
    totalActivities: Int = 0,
    currentLevel: Int = 1,
    onStartTracking: () -> Unit = {},
    onDiscoverTrails: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onOpenScout: () -> Unit = {},
    onViewHistory: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    statsLoading: Boolean = false,
    statsError: Boolean = false,
    signedIn: Boolean = true,
    onRetry: () -> Unit = {},
    weatherContent: @Composable () -> Unit = {}
) {
    val gutter = Modifier.padding(horizontal = 20.dp)
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeHero(
                    title = if (userName.isNullOrBlank()) stringResource(R.string.home_welcome_generic) else stringResource(R.string.home_welcome, userName.isolateBidi()),
                    subtitle = stringResource(R.string.home_intro),
                    initial = userName?.trim()?.firstOrNull()?.uppercaseChar()?.toString(),
                    onViewProfile = onViewProfile
                )
            }
            item {
                // The totals card rides up over the hero's lower edge.
                NatureCard(modifier = gutter.overlapAbove(36.dp), contentPadding = PaddingValues(20.dp)) {
                    Text(
                        stringResource(R.string.home_totals),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        !signedIn -> Text(stringResource(R.string.home_sign_in), style = MaterialTheme.typography.bodyLarge)
                        statsLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        statsError -> {
                            Text(stringResource(R.string.load_failed), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.home_distance, totalDistance / 1000),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        pluralStringResource(R.plurals.home_activity_count, totalActivities, totalActivities),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                NatureTag(
                                    // Formatted with the app language's digits, like the distance beside it
                                    label = stringResource(R.string.profile_level, java.text.NumberFormat.getIntegerInstance().format(currentLevel)),
                                    color = PathyColors.Primary,
                                    icon = Icons.Filled.Terrain
                                )
                            }
                            if (totalActivities == 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.home_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                NaturePrimaryAction(
                    label = stringResource(R.string.home_track),
                    icon = Icons.Filled.FiberManualRecord,
                    onClick = onStartTracking,
                    iconTint = PathyColors.Error,
                    modifier = gutter
                )
            }
            item { Box(gutter) { weatherContent() } }
            item {
                Column(gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NatureActionTile(stringResource(R.string.home_map), Icons.Filled.Map, onOpenMap, Modifier.weight(1f).fillMaxHeight())
                        NatureActionTile(stringResource(R.string.home_discover), Icons.Filled.Explore, onDiscoverTrails, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NatureActionTile(stringResource(R.string.home_history), Icons.Filled.History, onViewHistory, Modifier.weight(1f).fillMaxHeight())
                        NatureActionTile(stringResource(R.string.home_scout), Icons.Filled.SmartToy, onOpenScout, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** Forest gradient with contour lines and mountains, the greeting, and the profile button. */
@Composable
private fun HomeHero(
    title: String,
    subtitle: String,
    initial: String?,
    onViewProfile: () -> Unit
) {
    val profileLabel = stringResource(R.string.home_profile)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(Forest800, Moss600)))
    ) {
        TopoBackground(modifier = Modifier.matchParentSize(), alpha = 0.10f)
        MountainRange(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.88f))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClickLabel = profileLabel, role = Role.Button, onClick = onViewProfile)
                    .semantics { contentDescription = profileLabel },
                contentAlignment = Alignment.Center
            ) {
                if (initial != null) {
                    Text(text = initial, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

/** Pulls an item up over the one above it: the item keeps its look, but takes [amount] less room. */
private fun Modifier.overlapAbove(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pull = amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - pull) { placeable.placeRelative(0, -pull) }
}

// ============================================
// 🎴 FEATURED TRAIL CARD
// ============================================

@Composable
fun FeaturedTrailCard(
    trailName: String,
    difficulty: String,
    distance: String,
    duration: String,
    rating: Float,
    gradient: List<Color>
) {
    Card(
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(gradient)
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Difficulty badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = difficulty,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = trailName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TrailInfoItem(
                        icon = Icons.Filled.Straighten,
                        text = distance
                    )
                    TrailInfoItem(
                        icon = Icons.Filled.Schedule,
                        text = duration
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = PathyColors.Highlight,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = rating.toString(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TrailInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

// ============================================
// 📋 RECENT ACTIVITY CARD
// ============================================

@Composable
fun RecentActivityCard(
    activityType: String,
    date: String,
    distance: String,
    duration: String
) {
    GlassMorphismCard(
        backgroundColor = Color.White.copy(alpha = 0.1f),
        borderColor = Color.White.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (activityType) {
                            "Hiking" -> Icons.Filled.Hiking
                            "Running" -> Icons.Filled.DirectionsRun
                            else -> Icons.Filled.DirectionsBike
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = activityType,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = date,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = distance,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = duration,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
