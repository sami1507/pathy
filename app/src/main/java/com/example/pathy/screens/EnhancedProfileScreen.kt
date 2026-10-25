package com.example.pathy.screens

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.design.pathyTopBarColors
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.components.*
import com.example.pathy.profile.UserProfile
import com.example.pathy.profile.UserLevelCalculator
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.PathyDimensions

/**
 * 👤 ENHANCED PROFILE SCREEN
 * 
 * Beautiful user profile with:
 * - Stats & achievements
 * - Activity history
 * - Settings
 * - Better than AllTrails + Strava!
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedProfileScreen(
    userProfile: UserProfile,
    onEditProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onActivityClick: (String) -> Unit = {},
    onAchievementClick: (String) -> Unit = {}
) {
    Scaffold(
        topBar = {
            ProfileTopBar(
                onSettings = onSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Profile header
            item {
                ProfileHeader(
                    userProfile = userProfile,
                    onEditClick = onEditProfile
                )
            }
            
            // Level & XP Progress
            item {
                LevelProgressCard(
                    userProfile = userProfile,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // Stats overview
            item {
                StatsOverviewSection(
                    stats = userProfile.stats,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Achievements
            item {
                AchievementsSection(
                    achievements = userProfile.achievements,
                    onViewAll = { /* Navigate to achievements */ },
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            // Activity breakdown
            item {
                ActivityBreakdownSection(
                    stats = userProfile.stats,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            // Streaks & Records
            item {
                StreaksAndRecordsSection(
                    stats = userProfile.stats,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar(onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.home_profile),
                fontWeight = FontWeight.Bold
            )
        },
        colors = pathyTopBarColors(),
        actions = {
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
    )
}

@Composable
fun ProfileHeader(
    userProfile: UserProfile,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(PathyColors.Primary, PathyColors.PrimaryDark)
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Profile picture
                Box {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(120.dp)
                    ) {
                        // Profile image would go here
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.background(PathyColors.Primary.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = userProfile.displayName.take(1).uppercase(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = PathyColors.Primary
                            )
                        }
                    }
                    
                    // Edit button
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .clickable(onClick = onEditClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.common_edit),
                                tint = PathyColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Name
                Text(
                    text = userProfile.displayName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                // Bio
                if (userProfile.bio.isNotEmpty()) {
                    Text(
                        text = userProfile.bio,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Location
                if (userProfile.location.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = userProfile.location,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Social stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SocialStatItem(
                        value = userProfile.followers.toString(),
                        label = stringResource(R.string.profile_followers)
                    )
                    SocialStatItem(
                        value = userProfile.following.toString(),
                        label = stringResource(R.string.profile_following)
                    )
                    SocialStatItem(
                        value = userProfile.stats.totalTrails.toString(),
                        label = stringResource(R.string.profile_trails)
                    )
                }
            }
        }
    }
}

@Composable
fun SocialStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun LevelProgressCard(
    userProfile: UserProfile,
    modifier: Modifier = Modifier
) {
    val currentXP = userProfile.experiencePoints
    val nextLevelXP = UserLevelCalculator.experienceForNextLevel(userProfile.level)
    val progress = currentXP.toFloat() / nextLevelXP.toFloat()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = PathyColors.Secondary.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.profile_level, userProfile.level),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PathyColors.Secondary
                    )
                    Text(
                        text = "$currentXP / $nextLevelXP XP",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Surface(
                    shape = CircleShape,
                    color = PathyColors.Secondary.copy(alpha = 0.2f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PathyColors.Secondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            PathyLinearProgress(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = PathyColors.Secondary
            )
        }
    }
}

@Composable
fun StatsOverviewSection(
    stats: com.example.pathy.profile.UserStats,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_overview),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PathyStatCard(
                icon = Icons.Filled.Straighten,
                value = "${(stats.totalDistance / 1000).toInt()}",
                label = stringResource(R.string.profile_km_total),
                color = PathyColors.Primary,
                modifier = Modifier.weight(1f)
            )
            
            PathyStatCard(
                icon = Icons.Filled.Hiking,
                value = "${stats.totalActivities}",
                label = "activities",
                color = PathyColors.Secondary,
                modifier = Modifier.weight(1f)
            )
            
            PathyStatCard(
                icon = Icons.Filled.Terrain,
                value = "${stats.totalElevationGain.toInt()}",
                label = stringResource(R.string.profile_m_climbed),
                color = PathyColors.Accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AchievementsSection(
    achievements: List<String>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.profile_achievements),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            TextButton(onClick = onViewAll) {
                Text(stringResource(R.string.common_view_all))
            }
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(achievements.take(5)) { achievement ->
                AchievementBadge(
                    achievement = achievement,
                    onClick = { /* Show achievement details */ }
                )
            }
            
            // Show more indicator
            if (achievements.size > 5) {
                item {
                    Card(
                        modifier = Modifier
                            .size(100.dp)
                            .clickable(onClick = onViewAll),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.common_more),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "+${achievements.size - 5}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementBadge(
    achievement: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PathyColors.Primary.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = PathyColors.Primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievement.take(10),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun ActivityBreakdownSection(
    stats: com.example.pathy.profile.UserStats,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_activity_breakdown),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        ActivityTypeCard(
            icon = Icons.Filled.Hiking,
            name = stringResource(R.string.activity_hiking),
            distance = stats.hikingDistance,
            color = PathyColors.Primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ActivityTypeCard(
            icon = Icons.Filled.DirectionsRun,
            name = stringResource(R.string.activity_running),
            distance = stats.runningDistance,
            color = PathyColors.Secondary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ActivityTypeCard(
            icon = Icons.Filled.DirectionsBike,
            name = stringResource(R.string.activity_cycling),
            distance = stats.cyclingDistance,
            color = PathyColors.Warning
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ActivityTypeCard(
            icon = Icons.Filled.DirectionsBike,
            name = stringResource(R.string.activity_mtb),
            distance = stats.mtbDistance,
            color = PathyColors.Berry
        )
    }
}

@Composable
fun ActivityTypeCard(
    icon: ImageVector,
    name: String,
    distance: Float,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Column {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.unit_km, (distance / 1000).toInt()),
                        fontSize = 14.sp,
                        color = color
                    )
                }
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StreaksAndRecordsSection(
    stats: com.example.pathy.profile.UserStats,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_streaks_records),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecordCard(
                icon = Icons.Filled.Whatshot,
                value = "${stats.currentStreak}",
                label = stringResource(R.string.profile_day_streak),
                color = PathyColors.Accent,
                modifier = Modifier.weight(1f)
            )
            
            RecordCard(
                icon = Icons.Filled.Timer,
                value = "${stats.longestStreak}",
                label = stringResource(R.string.profile_best_streak),
                color = PathyColors.Warning,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecordCard(
                icon = Icons.Filled.Straighten,
                value = "${(stats.longestDistance / 1000).toInt()}",
                label = stringResource(R.string.profile_longest_km),
                color = PathyColors.Primary,
                modifier = Modifier.weight(1f)
            )
            
            RecordCard(
                icon = Icons.Filled.Terrain,
                value = "${stats.highestElevation.toInt()}",
                label = stringResource(R.string.profile_highest_m),
                color = PathyColors.Accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun RecordCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
