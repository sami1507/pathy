package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.pathy.tracking.ActivityStats
import com.example.pathy.tracking.RecordingState
import com.example.pathy.ui.design.TopoBackground
import com.example.pathy.ui.theme.Forest800
import com.example.pathy.ui.theme.Moss600
import com.example.pathy.ui.theme.Mist50
import com.example.pathy.ui.theme.PathyColors
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * 🏃 TRACKING SCREEN
 * 
 * Beautiful real-time activity tracking
 * - Live stats display
 * - Recording controls
 * - Map view
 * - Better than AllTrails + Strava combined!
 */

@Composable
fun TrackingScreen(
    recordingState: RecordingState,
    stats: ActivityStats,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit = {}
) {
    // Pulsing recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Nature backdrop: forest gradient with contour lines (the live map is recorded separately)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Forest800, Moss600, Mist50)))
        ) {
            TopoBackground(modifier = Modifier.fillMaxSize(), color = Color.White, alpha = 0.08f)
        }

        // Top bar
        TrackingTopBar(
            recordingState = recordingState,
            onClose = onClose,
            pulse = pulse
        )

        // Stats cards
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatsGrid(stats = stats)
        }

        // Bottom control panel
        BottomControlPanel(
            recordingState = recordingState,
            onStartClick = onStart,
            onPauseClick = onPause,
            onResumeClick = onResume,
            onFinishClick = onFinish,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun TrackingTopBar(
    recordingState: RecordingState,
    onClose: () -> Unit,
    pulse: Float
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = PathyColors.TextPrimary
                )
            }
            
            // Recording indicator
            if (recordingState is RecordingState.Recording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PathyColors.Error,
                        modifier = Modifier
                            .size(12.dp)
                            .scale(pulse)
                    ) {}
                    Text(
                        text = stringResource(
                            if (recordingState.autoPaused) R.string.rec_auto_paused_label else R.string.tracking_recording
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PathyColors.Error
                    )
                }
            } else if (recordingState is RecordingState.Paused) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PathyColors.Warning,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Text(
                        text = stringResource(R.string.tracking_paused),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PathyColors.Warning
                    )
                }
            }
            
            // Settings
            IconButton(onClick = { /* Settings */ }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = PathyColors.TextPrimary
                )
            }
        }
    }
}

@Composable
fun StatsGrid(stats: ActivityStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Distance (largest card)
        LargeStatCard(
            value = formatDistance(stats.distance),
            label = stringResource(R.string.stat_distance),
            icon = Icons.Filled.Straighten,
            gradient = listOf(PathyColors.Primary, PathyColors.PrimaryDark)
        )
        
        // Duration
        LargeStatCard(
            value = formatDuration(stats.duration),
            label = stringResource(R.string.stat_duration),
            icon = Icons.Filled.Schedule,
            gradient = listOf(PathyColors.Secondary, PathyColors.SecondaryDark)
        )
        
        // Two-column stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallStatCard(
                value = "${stats.currentSpeed.toInt()}",
                unit = "km/h",
                label = stringResource(R.string.stat_speed),
                icon = Icons.Filled.Speed,
                color = PathyColors.Accent,
                modifier = Modifier.weight(1f)
            )
            
            SmallStatCard(
                value = "${stats.elevationGain.toInt()}",
                unit = "m",
                label = stringResource(R.string.stat_elevation),
                icon = Icons.Filled.Terrain,
                color = PathyColors.Berry,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallStatCard(
                value = formatPace(stats.averagePace),
                unit = stringResource(R.string.unit_min_per_km),
                label = stringResource(R.string.stat_avg_pace),
                icon = Icons.Filled.Timer,
                color = PathyColors.Warning,
                modifier = Modifier.weight(1f)
            )
            
            SmallStatCard(
                value = "${stats.calories}",
                unit = "cal",
                label = stringResource(R.string.stat_calories),
                icon = Icons.Filled.LocalFireDepartment,
                color = PathyColors.Berry,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LargeStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    gradient: List<Color>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = value,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmallStatCard(
    value: String,
    unit: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        // A solid light card: a faint tint of the number's colour over the green background was unreadable.
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = unit,
                        fontSize = 14.sp,
                        color = color.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BottomControlPanel(
    recordingState: RecordingState,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (recordingState) {
                is RecordingState.Idle -> {
                    // Start button
                    GradientButton(
                        text = stringResource(R.string.tracking_start),
                        icon = Icons.Filled.PlayArrow,
                        onClick = onStartClick,
                        gradient = listOf(PathyColors.Primary, PathyColors.PrimaryDark)
                    )
                }
                
                is RecordingState.Recording -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Pause button
                        OutlineButton(
                            text = stringResource(R.string.tracking_pause),
                            icon = Icons.Filled.Pause,
                            onClick = onPauseClick,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Finish button
                        GradientButton(
                            text = stringResource(R.string.tracking_finish),
                            icon = Icons.Filled.Stop,
                            onClick = onFinishClick,
                            gradient = listOf(PathyColors.Berry, PathyColors.Bark),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                is RecordingState.Paused -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Resume button
                        GradientButton(
                            text = stringResource(R.string.tracking_resume),
                            icon = Icons.Filled.PlayArrow,
                            onClick = onResumeClick,
                            gradient = listOf(PathyColors.Primary, PathyColors.PrimaryDark),
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Finish button
                        OutlineButton(
                            text = stringResource(R.string.tracking_finish),
                            icon = Icons.Filled.Stop,
                            onClick = onFinishClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    gradient: List<Color>,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun OutlineButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 2.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Utility functions
@Composable
private fun formatDistance(meters: Float): String {
    return if (meters < 1000) {
        stringResource(R.string.unit_m, meters.toInt())
    } else {
        stringResource(R.string.unit_km, "%.2f".format(meters / 1000))
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatPace(pace: Float): String {
    if (pace == 0f || pace.isInfinite() || pace.isNaN()) return "0:00"
    val minutes = pace.toInt()
    val seconds = ((pace - minutes) * 60).toInt()
    return String.format("%d:%02d", minutes, seconds)
}
