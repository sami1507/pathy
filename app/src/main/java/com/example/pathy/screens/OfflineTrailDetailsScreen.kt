package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.theme.MapPalette

import com.example.pathy.ui.theme.PathyColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathy.calculateBounds
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.toTrailData
import com.example.pathy.offline.OfflineRegions
import com.example.pathy.TrailNavigationActivity
import android.content.Intent
import com.google.gson.Gson
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location

@Composable
fun OfflineTrailDetailsScreen(trail: SavedTrail) {
    val context = LocalContext.current
    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(PathyColors.PrimaryLight, PathyColors.PrimaryLight)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        // Guidance works from the saved line and the GPS, so it needs no connection.
                        val first = trail.geoPoints.firstOrNull() ?: return@Button
                        context.startActivity(
                            Intent(context, TrailNavigationActivity::class.java)
                                .putExtra("trail", Gson().toJson(trail.toTrailData()))
                                .putExtra("lat", first.latitude)
                                .putExtra("lng", first.longitude)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, PathyColors.Primary, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryDark)
                ) {
                    Text(stringResource(R.string.trail_start_button), color = Color.White)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                TrailMapOffline(trail)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("📍 ${trail.name}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.trail_offline_hint), style = MaterialTheme.typography.bodySmall, color = PathyColors.Primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.trail_length_line, trail.distanceInMeters), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.trail_time_line, trail.durationInMillis / 60000), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.trail_rating_line, trail.rating), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.trail_difficulty_line, trail.difficulty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun TrailMapOffline(trail: SavedTrail) {
    val context = LocalContext.current
    val points = trail.geoPoints.map { Point.fromLngLat(it.longitude, it.latitude) }

    AndroidView(factory = {
        MapView(context).apply {
            val mapboxMap = getMapboxMap()

            // The same style the area was saved in: with no connection the map reads it from the phone.
            mapboxMap.loadStyle(
                styleExtension = style(OfflineRegions.STYLE) {},
                onStyleLoaded = { style ->
                    getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)
                        ?.updateSettings { enabled = false }

                    getPlugin<com.mapbox.maps.plugin.logo.LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)
                        ?.updateSettings { enabled = false }

                    gestures.updateSettings {
                        scrollEnabled = true
                        rotateEnabled = true
                        pitchEnabled = true
                    }

                    location.updateSettings {
                        enabled = false
                    }

                    if (points.size >= 2) {
                        val bounds = calculateBounds(points)
                        val cameraOptions = mapboxMap.cameraForCoordinateBounds(
                            bounds,
                            EdgeInsets(100.0, 100.0, 100.0, 100.0),
                            0.0,
                            0.0
                        )
                        mapboxMap.setCamera(cameraOptions)

                        val line = LineString.fromLngLats(points)
                        val source = geoJsonSource("route-source") {
                            geometry(line)
                        }
                        style.addSource(source)

                        style.addLayer(lineLayer("route-border-layer", "route-source") {
                            lineColor(MapPalette.routeCasingHex)
                            lineWidth(16.0)
                        })
                        style.addLayer(lineLayer("route-layer", "route-source") {
                            lineColor(MapPalette.routeLineHex)
                            lineWidth(10.0)
                        })
                    }
                }
            )
        }
    }, modifier = Modifier.fillMaxSize())
}
