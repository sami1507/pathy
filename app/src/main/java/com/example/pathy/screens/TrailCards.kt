package com.example.pathy.screens

import com.example.pathy.i18n.isolateBidi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.R
import com.example.pathy.data.EnhancedTrailData
import com.example.pathy.data.TrailAPIService
import com.example.pathy.data.TrailInfo
import com.example.pathy.formatDifficulty
import com.example.pathy.i18n.TrailInfoLabels
import com.example.pathy.ui.theme.PathyColors
import kotlin.math.roundToInt

/**
 * A trail in a list. The whole card is one button for a screen reader, read out as a sentence
 * ("Wadi trail, 8 km, moderate, 3 km away"); the picture and chips are not read twice.
 */
@Composable
fun PremiumTrailCard(trail: EnhancedTrailData, onClick: () -> Unit) {
    val context = LocalContext.current
    val name = TrailInfoLabels.listName(context, trail.name, trail.info)
    val km = stringResource(R.string.unit_km, String.format(java.util.Locale.getDefault(), "%.1f", trail.distanceInMeters / 1000))
    val difficulty = formatDifficulty(context, trail.difficulty)
    val away = trail.distanceFromUser?.let { stringResource(R.string.trail_away, com.example.pathy.formatDistance(context, it)) }
    val summary = listOfNotNull(name, km, difficulty, away).joinToString(", ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = summary
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            TrailBanner(trail, name)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TrailFact(Icons.Filled.Straighten, km, stringResource(R.string.stat_distance))
                    TrailFact(
                        Icons.Filled.Terrain,
                        trail.elevationGain?.let { stringResource(R.string.unit_m, it) } ?: "–",
                        stringResource(R.string.stat_elevation)
                    )
                    TrailFact(
                        Icons.Filled.AccessTime,
                        durationText(trail.estimatedDuration),
                        stringResource(R.string.stat_duration)
                    )
                }
                TrailChips(trail.info)
                val place = trail.locationName.takeIf { it.isNotBlank() }
                if (place != null || away != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = listOfNotNull(place?.isolateBidi(), away).joinToString(" · "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailBanner(trail: EnhancedTrailData, name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(Brush.linearGradient(listOf(PathyColors.PrimaryDark, PathyColors.Primary)))
    ) {
        Icon(
            imageVector = typeIcon(trail.trailType),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(84.dp).align(Alignment.CenterEnd).padding(end = 12.dp)
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(0.8f)
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { DifficultyBadge(trail.difficulty) }
        trail.info?.waymark?.let { colour ->
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(markColor(colour))
                    .semantics { }
            )
        }
    }
}

private fun typeIcon(type: String): ImageVector = when (type) {
    TrailAPIService.TYPE_HIKING -> Icons.Filled.Hiking
    TrailAPIService.TYPE_MTB, TrailAPIService.TYPE_CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    TrailAPIService.TYPE_OFFROAD -> Icons.Filled.DriveEta
    else -> Icons.Filled.Terrain
}

private fun durationText(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes < 60) "$minutes′" else String.format(java.util.Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}

@Composable
private fun TrailFact(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(18.dp), tint = PathyColors.Primary)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The paint colour of a waymarked trail, as a dot a hiker can match with the marks on the rocks. */
internal fun markColor(colour: String): Color = when (colour) {
    "red" -> Color(0xFFD32F2F)
    "blue" -> Color(0xFF1976D2)
    "green" -> Color(0xFF2E7D32)
    "black" -> Color(0xFF212121)
    "orange" -> Color(0xFFEF6C00)
    "yellow" -> Color(0xFFFBC02D)
    "white" -> Color(0xFFFFFFFF)
    "purple" -> Color(0xFF7B1FA2)
    "brown" -> Color(0xFF6D4C41)
    "pink" -> Color(0xFFEC407A)
    else -> Color(0xFF9E9E9E)
}

/** What the data says about the trail: how it is marked, what to watch for, how recent it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrailChips(info: TrailInfo?, modifier: Modifier = Modifier) {
    if (info == null) return
    val context = LocalContext.current
    val warnings = remember(info) { info.warnings.mapNotNull { TrailInfoLabels.warning(context, it) } }
    val network = remember(info) { TrailInfoLabels.network(context, info.network) }
    val updated = remember(info) { TrailInfoLabels.updated(context, info) }
    if (info.waymark == null && network == null && warnings.isEmpty() && updated == null) return

    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (info.waymark != null) {
            InfoChip(stringResource(R.string.trail_chip_marked), Icons.Filled.Timeline, PathyColors.Primary, dot = markColor(info.waymark))
        }
        network?.let { InfoChip(it, Icons.Filled.Info, PathyColors.Primary) }
        warnings.forEach { InfoChip(it, Icons.Filled.Warning, PathyColors.Warning) }
        updated?.let { InfoChip(it, Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun InfoChip(text: String, icon: ImageVector, tint: Color, dot: Color? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot).padding(0.dp))
            } else {
                Icon(icon, null, Modifier.size(14.dp), tint = tint)
            }
            Spacer(Modifier.width(5.dp))
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
