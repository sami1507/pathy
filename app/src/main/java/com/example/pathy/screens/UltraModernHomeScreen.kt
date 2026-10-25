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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.ui.components.*
import java.util.*
import androidx.compose.ui.res.stringResource
import com.example.pathy.R

/**
 * 🌟 ULTRA-MODERN HOME SCREEN
 * 
 * STUNNING new design with:
 * - Glass morphism effects
 * - Animated gradients
 * - Premium feel
 * - Smooth animations
 * - BETTER THAN ALL COMPETITORS!
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
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                com.example.pathy.ui.design.MountainHero(
                    title = if (userName.isNullOrBlank()) stringResource(R.string.home_welcome_generic) else stringResource(R.string.home_welcome, userName.isolateBidi()),
                    subtitle = stringResource(R.string.home_intro)
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.home_totals), style = MaterialTheme.typography.titleMedium)
                        when {
                            !signedIn -> Text(stringResource(R.string.home_sign_in))
                            statsLoading -> CircularProgressIndicator()
                            statsError -> {
                                Text(stringResource(R.string.load_failed))
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                            else -> {
                                Text(stringResource(R.string.home_distance, totalDistance / 1000), style = MaterialTheme.typography.headlineSmall)
                                Text(pluralStringResource(R.plurals.home_activity_count, totalActivities, totalActivities))
                                if (totalActivities == 0) Text(stringResource(R.string.home_empty))
                            }
                        }
                    }
                }
            }
            item { Button(onClick = onStartTracking, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_track)) } }
            item { OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_map)) } }
            item { OutlinedButton(onClick = onDiscoverTrails, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_discover)) } }
            item { weatherContent() }
            item { OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_history)) } }
            item { OutlinedButton(onClick = onOpenScout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_scout)) } }
            item { OutlinedButton(onClick = onViewProfile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_profile)) } }
        }
    }
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
