package com.example.pathy.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathy.R
import com.example.pathy.export.GpxShare
import com.example.pathy.history.ActivityDetails
import com.example.pathy.i18n.activityTypeLabel
import com.example.pathy.i18n.applyAppLanguage
import com.example.pathy.tracking.ActivityDocument
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.theme.MapPalette
import com.example.pathy.ui.theme.PathyColors
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiPoint
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.logo.LogoPlugin
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * One recorded outing: the route on a map, the numbers, the elevation profile, and what you can do
 * with it - share it as a GPX file (any other hiking app or GPS watch reads it) or delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailsScreen(
    details: ActivityDetails,
    onBack: () -> Unit,
    onDelete: suspend () -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activityTypeLabel(details.activityType), fontWeight = FontWeight.Bold) },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { askDelete = true }, enabled = !busy) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.activity_delete))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (details.routePoints.size >= 2) {
                RouteMap(details, Modifier.fillMaxWidth().height(280.dp))
            } else {
                Text(
                    stringResource(R.string.activity_no_route),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (details.title.isNotBlank()) Text(details.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(details.startTime)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatsGrid(details)

                val altitudes = details.routePoints.map { it.altitude }.filter { it != 0.0 }
                if (altitudes.size >= 2) {
                    Text(stringResource(R.string.activity_stat_elevation_chart), fontWeight = FontWeight.Bold)
                    ElevationChart(altitudes, Modifier.fillMaxWidth().height(110.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = !busy && details.routePoints.size >= 2,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val intent = GpxShare.intentFor(
                                        context,
                                        details.title.ifBlank { activityTitleFor(details) },
                                        ActivityDocument.trackPoints(details.routePoints)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    message = context.getString(R.string.activity_export_failed)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Share, null)
                        Text(stringResource(R.string.activity_export_gpx), Modifier.padding(start = 8.dp))
                    }
                }
                message?.let { Text(it, color = PathyColors.Error) }
                Box(Modifier.height(24.dp))
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(R.string.activity_delete_title)) },
            text = { Text(stringResource(R.string.activity_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    scope.launch {
                        busy = true
                        val ok = onDelete()
                        busy = false
                        if (!ok) message = context.getString(R.string.activity_delete_failed)
                    }
                }) { Text(stringResource(R.string.activity_delete), color = PathyColors.Error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

private fun activityTitleFor(d: ActivityDetails): String =
    "${d.activityType.lowercase()}-" + android.text.format.DateFormat.format("yyyy-MM-dd-HHmm", d.startTime)

@Composable
private fun StatsGrid(d: ActivityDetails) {
    val minutes = (d.duration / 60_000).toInt()
    val rows = listOf(
        listOf(
            stringResource(R.string.stat_distance) to stringResource(R.string.unit_km, String.format(Locale.getDefault(), "%.2f", d.distance / 1000)),
            stringResource(R.string.stat_duration) to pluralStringResource(R.plurals.minutes_count, minutes, minutes)
        ),
        listOf(
            stringResource(R.string.stat_elevation) to stringResource(R.string.unit_m, d.elevationGain.toInt()),
            stringResource(R.string.activity_stat_calories) to stringResource(R.string.unit_kcal, d.calories.toString())
        ),
        listOf(
            stringResource(R.string.activity_stat_avg_speed) to stringResource(R.string.unit_kmh, String.format(Locale.getDefault(), "%.1f", d.averageSpeed)),
            stringResource(R.string.activity_stat_max_speed) to stringResource(R.string.unit_kmh, String.format(Locale.getDefault(), "%.1f", d.maxSpeed))
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A line chart of altitude along the route, drawn directly (no chart library needed for one series). */
@Composable
private fun ElevationChart(altitudes: List<Double>, modifier: Modifier = Modifier) {
    val line = PathyColors.Primary
    val fill = Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), Color.Transparent))
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val lo = altitudes.min()
        val hi = altitudes.max()
        val span = (hi - lo).coerceAtLeast(10.0) // a flat walk should look flat, not stretched
        val stepX = size.width / (altitudes.size - 1)
        fun y(v: Double) = (size.height - ((v - lo) / span * (size.height * 0.8f) + size.height * 0.1f)).toFloat()

        val path = Path().apply {
            moveTo(0f, y(altitudes[0]))
            altitudes.forEachIndexed { i, v -> lineTo(i * stepX, y(v)) }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun RouteMap(details: ActivityDetails, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val points = remember(details) { details.routePoints.map { Point.fromLngLat(it.lon, it.lat) } }

    LaunchedEffect(points) {
        mapView.getMapboxMap().loadStyleUri(Style.OUTDOORS) { style ->
            style.applyAppLanguage(context)
            mapView.getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)?.updateSettings { enabled = false }
            mapView.getPlugin<LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)?.updateSettings { enabled = false }
            style.addSource(geoJsonSource("route-source") { geometry(LineString.fromLngLats(points)) })
            style.addLayer(lineLayer("route-casing", "route-source") {
                lineColor(MapPalette.routeCasingHex)
                lineWidth(9.0)
            })
            style.addLayer(lineLayer("route-line", "route-source") {
                lineColor(MapPalette.routeLineHex)
                lineWidth(5.0)
            })
            style.addSource(geoJsonSource("route-ends") { geometry(MultiPoint.fromLngLats(listOf(points.first(), points.last()))) })
            style.addLayer(circleLayer("route-ends-layer", "route-ends") {
                circleColor(MapPalette.routeLineHex)
                circleRadius(7.0)
                circleStrokeColor(MapPalette.routeCasingHex)
                circleStrokeWidth(3.0)
            })
            val camera = mapView.getMapboxMap().cameraForCoordinates(points, EdgeInsets(60.0, 60.0, 60.0, 60.0), null, null)
            mapView.getMapboxMap().setCamera(camera)
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
