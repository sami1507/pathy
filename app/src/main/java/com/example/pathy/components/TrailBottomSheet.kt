package com.example.pathy.components

import androidx.compose.ui.res.pluralStringResource
import com.example.pathy.i18n.tagLabel
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.pathy.data.EnhancedTrailData
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.PathyDimensions

/**
 * 🏔️ BEAUTIFUL TRAIL INFO BOTTOM SHEET
 * 
 * Shows when user taps a trail on the map
 * Gorgeous design with all trail details!
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailHeader(trail: EnhancedTrailData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(PathyDimensions.RadiusL))
            .background(
                Brush.linearGradient(
                    listOf(
                        getTrailTypeColor(trail.trailType),
                        getTrailTypeColor(trail.trailType).copy(alpha = 0.7f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PathyDimensions.SpaceL),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PathyBadge(
                    text = trail.trailType,
                    icon = getTrailTypeIcon(trail.trailType),
                    backgroundColor = Color.White.copy(alpha = 0.3f),
                    textColor = Color.White
                )
                
                PathyBadge(
                    text = trail.difficulty.name,
                    backgroundColor = getDifficultyColor(trail.difficulty.name).copy(alpha = 0.3f),
                    textColor = Color.White
                )
            }
            
            // Trail name and rating
            Column {
                Text(
                    text = trail.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) { index ->
                        Icon(
                            imageVector = if (index < trail.rating.toInt()) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = PathyColors.Highlight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = "${trail.rating} (${pluralStringResource(R.plurals.reviews_count, trail.reviewCount, trail.reviewCount)})",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TrailQuickStats(trail: EnhancedTrailData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickStatItem(
            icon = Icons.Filled.Straighten,
            value = String.format("%.1f", trail.distanceInMeters / 1000.0),
            unit = stringResource(R.string.unit_km_short),
            label = stringResource(R.string.stat_distance)
        )
        
        QuickStatItem(
            icon = Icons.Filled.Terrain,
            value = trail.elevationGain?.toString() ?: "–",
            unit = if (trail.elevationGain != null) "m" else "",
            label = stringResource(R.string.stat_elevation)
        )
        
        QuickStatItem(
            icon = Icons.Filled.Schedule,
            value = trail.estimatedDuration.toString(),
            unit = stringResource(R.string.unit_min_short),
            label = stringResource(R.string.stat_duration)
        )
    }
}

@Composable
fun QuickStatItem(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = PathyColors.PrimaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PathyColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = " $unit",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TrailDescription(description: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.sheet_about),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun TrailInfoGrid(trail: EnhancedTrailData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
    ) {
        Text(
            text = stringResource(R.string.sheet_trail_info),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(PathyDimensions.RadiusM)
        ) {
            Column(
                modifier = Modifier.padding(PathyDimensions.SpaceM),
                verticalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
            ) {
                InfoRow(
                    icon = Icons.Filled.Route,
                    label = stringResource(R.string.sheet_route_type),
                    value = if (trail.isLoop) stringResource(R.string.sheet_loop) else stringResource(R.string.sheet_one_way)
                )
                
                InfoRow(
                    icon = Icons.Filled.Hiking,
                    label = stringResource(R.string.sheet_trail_type),
                    value = trail.trailType
                )
                
                InfoRow(
                    icon = Icons.Filled.Terrain,
                    label = stringResource(R.string.sheet_surface),
                    value = trail.surface
                )
                
                if (trail.features.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Column {
                        Text(
                            text = stringResource(R.string.sheet_features),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PathyTagRow(
                            tags = trail.features.map { tagLabel(it) },
                            color = PathyColors.Primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PathyColors.Primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TrailConditions(conditions: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PathyColors.Info.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(PathyDimensions.RadiusM)
    ) {
        Row(
            modifier = Modifier.padding(PathyDimensions.SpaceM),
            horizontalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = PathyColors.Info,
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = stringResource(R.string.sheet_current_conditions),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = conditions,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TrailActionButtons(
    onNavigate: () -> Unit,
    onViewDetails: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PathyDimensions.SpaceS)
    ) {
        PathyButton(
            text = stringResource(R.string.sheet_start_navigation),
            icon = Icons.Filled.Navigation,
            onClick = onNavigate,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.PRIMARY
        )
        
        PathyButton(
            text = stringResource(R.string.sheet_view_full_details),
            icon = Icons.Filled.Info,
            onClick = onViewDetails,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.SECONDARY
        )
    }
}

// Helper functions
private fun getTrailTypeColor(type: String): Color {
    return when (type.uppercase().replace("-", "_")) {
        "HIKING" -> PathyColors.Primary
        "MTB", "MOUNTAIN_BIKING" -> PathyColors.Accent
        "CYCLING" -> PathyColors.Secondary
        "OFFROAD" -> PathyColors.Warning
        else -> PathyColors.Primary
    }
}

private fun getDifficultyColor(difficulty: String): Color {
    return when (difficulty.uppercase()) {
        "EASY" -> PathyColors.Primary
        "MODERATE", "MEDIUM" -> PathyColors.Warning
        "HARD" -> PathyColors.Accent
        "EXTREME" -> PathyColors.DifficultyExtreme
        else -> PathyColors.Primary
    }
}

private fun getTrailTypeIcon(type: String): ImageVector {
    return when (type.uppercase().replace("-", "_")) {
        "HIKING" -> Icons.Filled.Hiking
        "MTB", "MOUNTAIN_BIKING" -> Icons.Filled.DirectionsBike
        "CYCLING" -> Icons.Filled.DirectionsBike
        "OFFROAD" -> Icons.Filled.DirectionsCar
        else -> Icons.Filled.Hiking
    }
}
