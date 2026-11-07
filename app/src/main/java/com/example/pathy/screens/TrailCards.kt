package com.example.pathy.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DriveEta
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.EnhancedTrailData
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.TrailAPIService
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailInfo
import com.example.pathy.formatDifficulty
import com.example.pathy.formatDistance
import com.example.pathy.i18n.TrailInfoLabels
import com.example.pathy.i18n.isolateBidi
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NatureTag
import com.example.pathy.ui.design.TrailSketchTile
import com.example.pathy.ui.design.difficultyColor
import com.example.pathy.ui.theme.PathyColors

/** Everything one trail card shows, already turned into text, so the card itself can be drawn and tested on its own. */
internal data class TrailCardModel(
    val name: String,
    val place: String?,
    val away: String?,
    val distanceKm: String,
    val gainM: Int?,
    val durationMs: Long,
    val difficulty: DifficultyLevel,
    val points: List<LatLngDTO>,
    val kindIcon: ImageVector,
    val info: TrailInfo?
)

@Composable
internal fun EnhancedTrailData.toCardModel(): TrailCardModel {
    val context = LocalContext.current
    return TrailCardModel(
        name = TrailInfoLabels.listName(context, name, info),
        place = locationName.takeIf { it.isNotBlank() }?.isolateBidi(),
        away = distanceFromUser?.let { stringResource(R.string.trail_away, formatDistance(context, it)) },
        distanceKm = kmText(distanceInMeters),
        gainM = elevationGain,
        durationMs = estimatedDuration,
        difficulty = difficulty,
        points = geoPoints,
        kindIcon = kindIconOf(trailType),
        info = info
    )
}

@Composable
internal fun TrailData.toCardModel(awayMeters: Double? = null): TrailCardModel {
    val context = LocalContext.current
    return TrailCardModel(
        name = TrailInfoLabels.listName(context, name, info),
        place = locationName.takeIf { it.isNotBlank() }?.isolateBidi(),
        away = awayMeters?.let { stringResource(R.string.trail_away, formatDistance(context, it)) },
        distanceKm = kmText(distanceInMeters),
        gainM = tags.elevationGain,
        durationMs = durationInMillis,
        difficulty = difficulty,
        points = geoPoints,
        kindIcon = if (isHikingTrail) Icons.Filled.Hiking else Icons.Filled.Terrain,
        info = info
    )
}

/** A trail in a list: its own shape, its name, the three numbers that decide whether to go, and how it is marked. */
@Composable
fun PremiumTrailCard(trail: EnhancedTrailData, onClick: () -> Unit, offline: Boolean = false) {
    TrailListCard(trail.toCardModel(), onClick, offline = offline)
}

/**
 * The one trail card of the app. The whole card is a single button for a screen reader, read out as a
 * sentence ("Wadi trail, 8.4 km, moderate, 3 km away"); the picture and chips are not read twice.
 * [trailing] is a slot at the top end, for a remove button on the saved list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrailListCard(
    model: TrailCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val difficultyText = formatDifficulty(context, model.difficulty)
    val km = stringResource(R.string.unit_km, model.distanceKm)
    val offlineText = stringResource(R.string.trail_tag_offline)
    val summary = listOfNotNull(model.name, km, difficultyText, model.away, offlineText.takeIf { offline }).joinToString(", ")

    NatureCard(
        onClick = onClick,
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(18.dp))) {
                TrailSketchTile(model.points, model.difficulty, Modifier.matchParentSize())
                Icon(
                    imageVector = model.kindIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(18.dp)
                )
                model.info?.waymark?.let { colour ->
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(markColor(colour))
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }
            Column(Modifier.weight(1f).heightIn(min = 104.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    trailing?.invoke()
                }
                val where = listOfNotNull(model.place, model.away).joinToString(" · ")
                if (where.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = where,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TrailFigure(Icons.Filled.Straighten, km)
                    model.gainM?.let { TrailFigure(Icons.Filled.TrendingUp, stringResource(R.string.unit_m, it.toString())) }
                    TrailFigure(Icons.Filled.AccessTime, durationText(model.durationMs))
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NatureTag(label = difficultyText, color = difficultyColor(model.difficulty))
            if (offline) NatureTag(label = offlineText, color = PathyColors.Primary, icon = Icons.Filled.CloudDone)
            TrailChipItems(model.info)
        }
    }
}

private fun kmText(meters: Double): String = String.format(java.util.Locale.getDefault(), "%.1f", meters / 1000)

private fun kindIconOf(type: String): ImageVector = when (type) {
    TrailAPIService.TYPE_HIKING -> Icons.Filled.Hiking
    TrailAPIService.TYPE_MTB, TrailAPIService.TYPE_CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    TrailAPIService.TYPE_OFFROAD -> Icons.Filled.DriveEta
    else -> Icons.Filled.Terrain
}

internal fun durationText(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes < 60) "$minutes′" else String.format(java.util.Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}

@Composable
private fun TrailFigure(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = PathyColors.Primary)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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

private class ChipSpec(val text: String, val icon: ImageVector, val tint: Color, val dot: Color? = null)

@Composable
private fun chipSpecs(info: TrailInfo?): List<ChipSpec> {
    if (info == null) return emptyList()
    val context = LocalContext.current
    val marked = stringResource(R.string.trail_chip_marked)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val warnings = remember(info) { info.warnings.mapNotNull { TrailInfoLabels.warning(context, it) } }
    val network = remember(info) { TrailInfoLabels.network(context, info.network) }
    val updated = remember(info) { TrailInfoLabels.updated(context, info) }
    return buildList {
        if (info.waymark != null) add(ChipSpec(marked, Icons.Filled.Timeline, PathyColors.Primary, dot = markColor(info.waymark)))
        network?.let { add(ChipSpec(it, Icons.Filled.Info, PathyColors.Primary)) }
        warnings.forEach { add(ChipSpec(it, Icons.Filled.Warning, PathyColors.Warning)) }
        updated?.let { add(ChipSpec(it, Icons.Filled.Info, muted)) }
    }
}

/** What the data says about the trail: how it is marked, what to watch for, how recent it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrailChips(info: TrailInfo?, modifier: Modifier = Modifier) {
    val specs = chipSpecs(info)
    if (specs.isEmpty()) return
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        specs.forEach { InfoChip(it) }
    }
}

/** The same chips, laid out inside a row the caller already owns. */
@Composable
private fun TrailChipItems(info: TrailInfo?) {
    chipSpecs(info).forEach { InfoChip(it) }
}

@Composable
private fun InfoChip(spec: ChipSpec) {
    Surface(
        shape = RoundedCornerShape(50),
        color = spec.tint.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, spec.tint.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (spec.dot != null) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(spec.dot))
            } else {
                Icon(spec.icon, null, Modifier.size(14.dp), tint = spec.tint)
            }
            Spacer(Modifier.width(5.dp))
            Text(spec.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
