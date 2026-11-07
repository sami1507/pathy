package com.example.pathy.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.LatLngDTO
import com.example.pathy.ui.theme.PathyColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** One colour per difficulty, used by the cards, the badges and the line on the map, so they always agree. */
fun difficultyColor(level: DifficultyLevel): Color = when (level) {
    DifficultyLevel.EASY -> PathyColors.DifficultyEasy
    DifficultyLevel.MEDIUM -> PathyColors.DifficultyModerate
    DifficultyLevel.HARD -> PathyColors.DifficultyHard
    DifficultyLevel.EXTREME -> PathyColors.DifficultyExtreme
}

/**
 * Fits a trail into a box of [width] x [height] for drawing. Longitude is scaled by the cosine of the
 * latitude, so the shape looks the way it does on the ground rather than stretched sideways. The route
 * is centred, keeps its proportions, and leaves [padding] free around it. Pure maths, so it is tested
 * without a screen.
 */
internal fun projectRoute(points: List<LatLngDTO>, width: Float, height: Float, padding: Float): List<Offset> {
    if (points.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val k = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)
    val spanX = ((maxLon - minLon) * k).toFloat()
    val spanY = (maxLat - minLat).toFloat()
    val availW = max(1f, width - 2 * padding)
    val availH = max(1f, height - 2 * padding)
    val scale = when {
        spanX <= 0f && spanY <= 0f -> 0f
        spanX <= 0f -> availH / spanY
        spanY <= 0f -> availW / spanX
        else -> min(availW / spanX, availH / spanY)
    }
    val offsetX = (width - spanX * scale) / 2f
    val offsetY = (height - spanY * scale) / 2f
    return points.map {
        Offset(
            x = offsetX + ((it.longitude - minLon) * k).toFloat() * scale,
            y = offsetY + (maxLat - it.latitude).toFloat() * scale
        )
    }
}

/** At most [max] points, always keeping the first and the last. A long trail has thousands; a thumbnail needs a few dozen. */
internal fun <T> downsample(points: List<T>, max: Int): List<T> {
    if (max < 2 || points.size <= max) return points
    val step = (points.size - 1).toDouble() / (max - 1)
    return List(max) { i -> points[min(points.lastIndex, Math.round(i * step).toInt())] }
}

/** True when the trail ends close to where it starts, so only one end marker is drawn. */
internal fun isLoop(points: List<Offset>, tolerance: Float): Boolean {
    if (points.size < 3) return false
    val a = points.first()
    val b = points.last()
    return abs(a.x - b.x) <= tolerance && abs(a.y - b.y) <= tolerance
}

/**
 * The shape of a trail, drawn from its own points. It needs no map and no connection, so a list of
 * trails looks the same on a mountain with no signal. The line is coloured by difficulty and sits on a
 * soft casing so it stays readable on any background.
 */
@Composable
fun TrailRouteSketch(
    points: List<LatLngDTO>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    casingColor: Color = MaterialTheme.colorScheme.surface,
    startColor: Color = PathyColors.Primary,
    endColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val sample = remember(points) { downsample(points, 120) }
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val pad = 14.dp.toPx()
        val projected = projectRoute(sample, size.width, size.height, pad)
        if (projected.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(projected.first().x, projected.first().y)
            projected.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, casingColor, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val start = projected.first()
        val end = projected.last()
        val ring = 7.dp.toPx()
        val dot = 5.dp.toPx()
        if (!isLoop(projected, tolerance = 6.dp.toPx())) {
            drawCircle(casingColor, ring, end)
            drawCircle(endColor, dot, end)
        }
        drawCircle(casingColor, ring, start)
        drawCircle(startColor, dot, start)
    }
}

/** The soft tile a [TrailRouteSketch] sits on: sage in the light theme, deep forest in the dark one, with faint contour lines. */
@Composable
fun TrailSketchTile(
    points: List<LatLngDTO>,
    difficulty: DifficultyLevel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
        TopoBackground(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            alpha = 0.07f,
            lineCount = 8
        )
        TrailRouteSketch(
            points = points,
            lineColor = difficultyColor(difficulty),
            modifier = Modifier.fillMaxSize()
        )
    }
}
