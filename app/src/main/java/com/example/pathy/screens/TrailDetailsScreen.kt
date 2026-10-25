package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.theme.MapPalette

import com.example.pathy.ui.design.MountainHero
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NatureSectionHeader
import com.example.pathy.ui.design.NatureStatTile
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.text.font.FontWeight

import com.example.pathy.ui.theme.PathyColors

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathy.OfflineTrailDetailsActivity
import com.example.pathy.TrailNavigationActivity
import com.example.pathy.bookmarkDataFor
import com.example.pathy.bookmarks.BookmarkManager
import com.example.pathy.calculateBounds
import com.example.pathy.trailClimb
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailReplayActivity
import com.example.pathy.data.from
import com.example.pathy.offline.OfflineRegions
import com.example.pathy.formatDifficulty
import com.example.pathy.formatDistance
import com.example.pathy.formatDuration
import com.example.pathy.getDisplayTrailName
import com.example.pathy.getMiddlePointLocationName
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailDetailsScreen(trail: TrailData) {
    val context = LocalContext.current
    var showLoadingDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    var trailTags by remember { mutableStateOf(trail.tags) }
    var isElevationLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (trailTags.elevationGain == null || trailTags.maxElevation == null) {
            isElevationLoading = true
            try {
                trailClimb(trail.geoPoints)?.let { (gain, max) ->
                    trailTags = trailTags.copy(elevationGain = gain, maxElevation = max)
                }
            } finally {
                isElevationLoading = false
            }
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .background(PathyColors.Primary),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StartTrailButton(
                        context = context,
                        trail = trail,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(start = 6.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(Color.Yellow.copy(alpha = 0.3f))
                    )
                    OptionsTrailButton(
                        onClick = { showBottomSheet = true },
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            MountainHero(
                title = getDisplayTrailName(context, trail),
                eyebrow = stringResource(R.string.field_trail),
                subtitle = trail.locationName.takeIf { it.isNotBlank() },
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NatureStatTile(Icons.Filled.Straighten, formatDistance(LocalContext.current, trail.distanceInMeters), stringResource(R.string.stat_length), Modifier.weight(1f))
                        NatureStatTile(Icons.Filled.Timer, formatDuration(trail.durationInMillis), stringResource(R.string.stat_time), Modifier.weight(1f))
                        NatureStatTile(Icons.Filled.Terrain, formatDifficulty(LocalContext.current, trail.difficulty), stringResource(R.string.stat_difficulty), Modifier.weight(1f))
                    }
                }
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            ) {
                TrailMap(trail)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrailStatusSection(trail)
                NatureSectionHeader(title = stringResource(R.string.trail_details_header))
                NatureCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    TrailDetailRow(
                        label = stringResource(R.string.stat_elevation_gain),
                        value = if (isElevationLoading) stringResource(R.string.common_loading) else trailTags.elevationGain?.let { stringResource(R.string.unit_m, it) } ?: stringResource(R.string.common_unavailable)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TrailDetailRow(
                        label = stringResource(R.string.stat_max_elevation),
                        value = if (isElevationLoading) stringResource(R.string.common_loading) else trailTags.maxElevation?.let { stringResource(R.string.unit_m, it) } ?: stringResource(R.string.common_unavailable)
                    )
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    if (showLoadingDialog) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.trail_downloading_offline), color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "${(downloadProgress * 100).toInt()}%",
                        color = Color.White
                    )
                }
            },
            containerColor = PathyColors.Bark,
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = PathyColors.Bark,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShareTrailButton(
                    context = context,
                    trail = trail.copy(tags = trailTags),
                    modifier = Modifier.fillMaxWidth()
                )
                SaveTrailToUserButton(
                    context = context,
                    trail = trail,
                    modifier = Modifier.fillMaxWidth()
                )
                BookmarkTrailButton(
                    trail = trail,
                    modifier = Modifier.fillMaxWidth()
                )
                DownloadTrailButton(
                    context = context,
                    trail = trail,
                    modifier = Modifier.fillMaxWidth(),
                    onStartDownload = { showLoadingDialog = true },
                    onProgressUpdate = { downloadProgress = it },
                    onEndDownload = {
                        showLoadingDialog = false
                        showBottomSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun OptionsTrailButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(55.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Text(stringResource(R.string.common_options), color = Color.White)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun StartTrailButton(context: Context, trail: TrailData, modifier: Modifier = Modifier) {
    Button(
        onClick = {
            // lastLocation throws SecurityException without the permission, so check first
            val hasLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasLocation) {
                Toast.makeText(context, context.getString(R.string.trail_location_permission_needed), Toast.LENGTH_LONG).show()
                return@Button
            }
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val intent = Intent(context, TrailNavigationActivity::class.java)
                    intent.putExtra("lat", location.latitude)
                    intent.putExtra("lng", location.longitude)
                    intent.putExtra("trail", Gson().toJson(trail))
                    context.startActivity(intent)
                } else {
                    // No recent position yet (the phone has just woken up, or GPS is still searching).
                    // Guidance finds the position itself, so open it at the start of the trail instead of refusing.
                    val first = trail.geoPoints.firstOrNull()
                    if (first == null) {
                        Toast.makeText(context, context.getString(R.string.trail_location_unavailable), Toast.LENGTH_LONG).show()
                    } else {
                        val intent = Intent(context, TrailNavigationActivity::class.java)
                        intent.putExtra("lat", first.latitude)
                        intent.putExtra("lng", first.longitude)
                        intent.putExtra("trail", Gson().toJson(trail))
                        context.startActivity(intent)
                    }
                }
            }
        },
        modifier = modifier
            .height(55.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryDark)
    ) {
        Text(stringResource(R.string.trail_start_button), color = Color.White)
    }
}

@Composable
fun DownloadTrailButton(
    context: Context,
    trail: TrailData,
    modifier: Modifier = Modifier,
    onStartDownload: () -> Unit,
    onProgressUpdate: (Float) -> Unit,
    onEndDownload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            scope.launch {
                isLoading = true
                onStartDownload()
                saveTrailToOffline(
                    context,
                    trail,
                    onProgress = { onProgressUpdate(it) }
                )
                onEndDownload()
                isLoading = false
            }
        },
        modifier = modifier
            .height(55.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.SecondaryDark),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(stringResource(R.string.trail_download_button), color = Color.White)
        }
    }
}

@Composable
fun SaveTrailToUserButton(context: Context, trail: TrailData, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Button(
        onClick = {
            scope.launch {
                isSaving = true
                try {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        val userTrailRef = FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUser.uid)
                            .collection("trails_user")
                            .document(trail.id)

                        userTrailRef.set(mapOf("trailId" to trail.id))
                            .addOnSuccessListener {
                                Toast.makeText(context, context.getString(R.string.trail_saved), Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, context.getString(R.string.trail_already_saved), Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(context, context.getString(R.string.error_not_signed_in), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.trail_save_error), Toast.LENGTH_SHORT).show()
                } finally {
                    isSaving = false
                }
            }
        },
        modifier = modifier
            .height(55.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Warning),
        enabled = !isSaving
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(stringResource(R.string.trail_save_button), color = Color.White)
        }
    }
}

@Composable
fun BookmarkTrailButton(trail: TrailData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val manager = remember { BookmarkManager() }
    var isBookmarked by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    // Load the user's bookmarks once so the button shows the right state
    LaunchedEffect(uid, trail.id) {
        if (uid != null) {
            manager.loadBookmarks(uid).onSuccess { isBookmarked = manager.isBookmarked(trail.id) }
        }
    }

    Button(
        onClick = {
            if (uid == null) {
                Toast.makeText(context, context.getString(R.string.bookmarks_sign_in_to_save), Toast.LENGTH_SHORT).show()
                return@Button
            }
            isBusy = true
            scope.launch {
                manager.toggleBookmark(uid, trail.id, trail.name, bookmarkDataFor(trail))
                    .onSuccess { nowBookmarked ->
                        isBookmarked = nowBookmarked
                        val msg = if (nowBookmarked) context.getString(R.string.bookmark_added) else context.getString(R.string.bookmark_removed)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(context, context.getString(R.string.error_generic_with_message, e.message), Toast.LENGTH_SHORT).show()
                    }
                isBusy = false
            }
        },
        modifier = modifier
            .height(55.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Berry),
        enabled = !isBusy
    ) {
        if (isBusy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(if (isBookmarked) stringResource(R.string.bookmark_remove_button) else stringResource(R.string.bookmark_add_button), color = Color.White)
        }
    }
}

@Composable
fun ShareTrailButton(context: Context, trail: TrailData, modifier: Modifier = Modifier) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val savedTrail = from(trail, userId)

    Button(
        onClick = {
            val firestore = FirebaseFirestore.getInstance()
            val trailId = savedTrail.id
            val ref = firestore.collection("trails").document(trailId)

            ref.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    ref.set(savedTrail)
                        .addOnSuccessListener {
                            shareLink(context, userId, trailId)
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
                        }
                } else {
                    shareLink(context, userId, trailId)
                }
            }
        },
        modifier = modifier
            .height(55.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Secondary)
    ) {
        Text(stringResource(R.string.share_button), color = Color.White)
    }
}

private fun shareLink(context: Context, sharedBy: String, trailId: String) {
    val link = "https://tempathy-e0c4b.web.app/trail?sharedBy=$sharedBy&trailId=$trailId"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_trail_message, link))
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_chooser_title)))
}



@Composable
fun TrailMap(trail: TrailData) {
    val context = LocalContext.current
    val trailJson = remember { Gson().toJson(trail) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
    ) {
        AndroidView(
            factory = {
                MapView(context).apply {
                    val mapboxMap = getMapboxMap()

                    mapboxMap.loadStyleUri(Style.OUTDOORS) { style ->
                        getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)?.updateSettings { enabled = false }
                        getPlugin<com.mapbox.maps.plugin.logo.LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)?.updateSettings { enabled = false }

                        gestures.updateSettings {
                            scrollEnabled = true
                            rotateEnabled = true
                            pitchEnabled = true
                        }

                        location.updateSettings {
                            enabled = true
                            pulsingEnabled = true
                            puckBearingEnabled = true
                        }

                        val points = trail.geoPoints.map { Point.fromLngLat(it.longitude, it.latitude) }

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
                }
            },
            modifier = Modifier
                .fillMaxSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    val intent = Intent(context, TrailReplayActivity::class.java)
                    intent.putExtra("trailJson", trailJson)
                    context.startActivity(intent)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.trail_play_video),
                tint = PathyColors.Secondary,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }
    }
}


suspend fun saveTrailToOffline(
    context: Context,
    trail: TrailData,
    onProgress: (Float) -> Unit
) {
    // The trail itself (its line and numbers) is a small file; the map around it is saved by Mapbox.
    val trailDir = File(context.filesDir, "trails/${trail.id}")
    if (!trailDir.exists()) trailDir.mkdirs()
    val trailFile = File(trailDir, "trail.json")
    trailFile.writeText(Gson().toJson(trail))
    File(trailDir, "path.json").writeText(Gson().toJson(trail.geoPoints))

    val downloaded = trail.geoPoints.size >= 2 && OfflineRegions.download(trail.id, trail.geoPoints, onProgress)
    if (!downloaded) {
        trailDir.deleteRecursively()
        Toast.makeText(context, context.getString(R.string.trail_offline_failed), Toast.LENGTH_LONG).show()
        return
    }
    Toast.makeText(context, context.getString(R.string.trail_saved_offline), Toast.LENGTH_SHORT).show()

    val intent = Intent(context, OfflineTrailDetailsActivity::class.java).apply {
        putExtra("offlineTrailPath", trailFile.absolutePath)
    }
    context.startActivity(intent)
    (context as? Activity)?.finish()
}

@Composable
private fun TrailDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}
