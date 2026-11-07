package com.example.pathy.screens

import android.Manifest
import android.app.Activity
import android.location.Location
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathy.LocationDisabledDialog
import com.example.pathy.PitchDialog
import com.example.pathy.R
import com.example.pathy.ReportDialogsAndSheets
import com.example.pathy.ReportPointsAnimation
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.nav.NavPhase
import com.example.pathy.core.nav.NavState
import com.example.pathy.core.nav.RouteNavigator
import com.example.pathy.core.nav.VoiceCueScheduler
import com.example.pathy.core.track.SportType
import com.example.pathy.data.CompassSensor
import com.example.pathy.data.MovementMode
import com.example.pathy.data.SavedTrail
import com.example.pathy.drawReportMarker
import com.example.pathy.formatDistance
import com.example.pathy.formatDuration
import com.example.pathy.i18n.AppLocale
import com.example.pathy.i18n.applyAppLanguage
import com.example.pathy.isLocationEnabled
import com.example.pathy.navigation.GuideIcon
import com.example.pathy.navigation.Guidance
import com.example.pathy.navigation.NavGuidance
import com.example.pathy.tracking.RecordingController
import com.example.pathy.tracking.RecordingHub
import com.example.pathy.tracking.RecordingState
import com.example.pathy.offline.OfflineRegions
import com.example.pathy.ui.theme.MapPalette
import com.example.pathy.ui.theme.PathyColors
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.LogoPlugin
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Guides a walker along a saved trail.
 *
 * What to say comes from [RouteNavigator], which follows the walker along the route itself: left and
 * right are taken from the shape of the trail, the walker can join anywhere along it, and leaving the
 * trail is noticed and announced. Recording the walk is the job of the recording service, the same one
 * the Record screen uses, so a navigated hike lands in the history like any other.
 */
@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
@Composable
fun TrailNavigationScreen(trail: SavedTrail, userLat: Double, userLng: Double) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val mapboxMap = mapView.getMapboxMap()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val route = remember(trail) { trail.geoPoints.map { LatLon(it.latitude, it.longitude) } }
    val trailPoints = remember(route) { route.map { Point.fromLngLat(it.lon, it.lat) } }
    val navigator = remember(route) { RouteNavigator(route) }
    val cues = remember(route) { VoiceCueScheduler() }

    var navState by remember { mutableStateOf<NavState?>(null) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var headingDeg by remember { mutableStateOf<Float?>(null) }
    var isCameraTrackingUser by remember { mutableStateOf(true) }
    var showPitchDialog by remember { mutableStateOf(false) }
    var pitchValue by remember { mutableStateOf(0.0) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var openHazardReportDialog by remember { mutableStateOf(false) }
    var openPointOfInterestDialog by remember { mutableStateOf(false) }
    var showReward by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var movementMode by remember { mutableStateOf(MovementMode.WALKING) }

    // Recording state comes from the service, so it is right even after the screen was closed and reopened.
    val recordingState by RecordingHub.state.collectAsState()
    val recordingStats by RecordingHub.stats.collectAsState()
    val recordedPosition by RecordingHub.position.collectAsState()
    val isRecording = recordingState !is RecordingState.Idle
    val walked = remember { mutableStateListOf<Point>() }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    val compass = remember { CompassSensor(context) }

    val locationRequest = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
    }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastLocation = location
                // A fix this poor says more about the signal than about where the walker is.
                if (location.hasAccuracy() && location.accuracy > MAX_USEFUL_ACCURACY_M) return

                val state = navigator.update(
                    LatLon(location.latitude, location.longitude),
                    if (location.hasAccuracy()) location.accuracy else null
                )
                navState = state

                // While walking the direction of travel is more reliable than the compass.
                if (location.hasBearing() && location.hasSpeed() && location.speed > 1f) headingDeg = location.bearing

                cues.next(state)?.let { cue ->
                    tts.value?.speak(NavGuidance.speech(context, cue), TextToSpeech.QUEUE_FLUSH, null, "nav-cue")
                }
                if (isCameraTrackingUser) {
                    mapboxMap.setCamera(CameraOptions.Builder().center(Point.fromLngLat(location.longitude, location.latitude)).build())
                }
            }
        }
    }

    // The screen stays on while guiding, and the sensors and updates stop with it.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        compass.start()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compass.stop()
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // Compass heading, used only when the walker is standing still. Changes under 4 degrees are noise.
    LaunchedEffect(Unit) {
        while (true) {
            val azimuth = compass.azimuth
            val current = headingDeg
            if (current == null || abs(azimuth - current) > 4f) headingDeg = azimuth
            delay(250L)
        }
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = AppLocale.speechLocale(context)
        }
        tts.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }

    // Tell the walker the outing is kept (or why it will wait), as the Record screen does.
    LaunchedEffect(Unit) {
        RecordingHub.finished.collect {
            val signedIn = FirebaseAuth.getInstance().currentUser != null
            Toast.makeText(
                context,
                context.getString(if (signedIn) R.string.rec_saved_on_phone else R.string.rec_sign_in_hint),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // The part of the walk recorded so far, drawn as it grows.
    LaunchedEffect(recordedPosition, isRecording) {
        if (!isRecording) {
            walked.clear()
        } else {
            recordedPosition?.let { walked.add(Point.fromLngLat(it.lon, it.lat)) }
        }
    }

    LaunchedEffect(trailPoints) {
        val trailLine = LineString.fromLngLats(trailPoints)
        mapboxMap.getStyle { style ->
            if (style.getSourceAs<GeoJsonSource>("trail-source") == null) {
                style.addSource(geoJsonSource("trail-source") { geometry(trailLine) })
                style.addLayer(lineLayer("trail-line", "trail-source") {
                    lineColor(MapPalette.routeUpcomingHex)
                    lineWidth(10.0)
                    lineBlur(1.0)
                })
                // Start and end of the trail
                style.addSource(geoJsonSource("trail-ends-source") {
                    geometry(com.mapbox.geojson.MultiPoint.fromLngLats(listOf(trailPoints.first(), trailPoints.last())))
                })
                style.addLayer(circleLayer("trail-ends-layer", "trail-ends-source") {
                    circleColor(MapPalette.routeLineHex)
                    circleRadius(8.0)
                    circleStrokeColor(MapPalette.routeCasingHex)
                    circleStrokeWidth(3.0)
                })
            } else {
                style.getSourceAs<GeoJsonSource>("trail-source")?.geometry(trailLine)
            }
        }
    }

    LaunchedEffect(walked.size) {
        if (walked.size < 2) return@LaunchedEffect
        val walkedLine = LineString.fromLngLats(walked.toList())
        mapboxMap.getStyle { style ->
            if (style.getSourceAs<GeoJsonSource>("walked-source") == null) {
                style.addSource(geoJsonSource("walked-source") { geometry(walkedLine) })
                style.addLayer(lineLayer("walked-border-layer", "walked-source") {
                    lineColor(MapPalette.routeCasingHex)
                    lineWidth(16.0)
                })
                style.addLayerAbove(
                    lineLayer("walked-line", "walked-source") {
                        lineColor(MapPalette.routeLineHex)
                        lineWidth(10.0)
                        lineBlur(1.0)
                    },
                    above = "trail-line"
                )
            } else {
                style.getSourceAs<GeoJsonSource>("walked-source")?.geometry(walkedLine)
            }
        }
    }

    // A coarse heading bucket keeps the banner from being rebuilt on every compass wobble.
    val headingBucket = headingDeg?.let { (it / 15f).toInt() }
    val guidance = remember(navState, headingBucket) { NavGuidance.of(context, navState, headingDeg) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    // The same style the trail was saved with, so the map is there with no signal.
                    // Mapbox Standard is not part of an offline region and shows a black screen.
                    getMapboxMap().loadStyleUri(OfflineRegions.STYLE) {
                        it.applyAppLanguage(context)
                        getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)?.updateSettings { enabled = false }
                        getPlugin<LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)?.updateSettings { enabled = false }
                        location.updateSettings {
                            enabled = true
                            pulsingEnabled = true
                            puckBearingEnabled = true
                        }
                        getMapboxMap().setCamera(
                            CameraOptions.Builder().center(Point.fromLngLat(userLng, userLat)).zoom(18.0).build()
                        )
                        gestures.addOnMoveListener(object : OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                isCameraTrackingUser = false
                            }

                            override fun onMove(detector: MoveGestureDetector): Boolean = false

                            override fun onMoveEnd(detector: MoveGestureDetector) {}
                        })
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isCameraTrackingUser) {
            IconButton(
                onClick = {
                    pitchValue = 0.0
                    lastLocation?.let {
                        mapboxMap.flyTo(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(it.longitude, it.latitude))
                                .pitch(pitchValue)
                                .zoom(19.0)
                                .build(),
                            mapAnimationOptions { duration(2000L) }
                        )
                        isCameraTrackingUser = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, bottom = 120.dp)
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                    .border(2.dp, PathyColors.PrimaryDark, CircleShape)
                    .shadow(6.dp, shape = CircleShape)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.nav_center_me_desc),
                    tint = PathyColors.PrimaryDark,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        PitchDialog(
            showDialog = showPitchDialog,
            onDismiss = { showPitchDialog = false },
            pitchValue = pitchValue,
            onPitchChange = { pitchValue = it },
            mapView = mapView
        )

        Box(Modifier.fillMaxSize()) {
            NavigationInstructionBox(
                guidance = guidance,
                progress = navState?.takeIf { it.phase == NavPhase.ON_ROUTE || it.phase == NavPhase.ARRIVED }?.progress,
                offRoute = navState?.phase == NavPhase.OFF_ROUTE,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            IconButton(
                onClick = { showPitchDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 12.dp)
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                    .clip(CircleShape)
            ) {
                Icon(Icons.Filled.Terrain, contentDescription = stringResource(R.string.nav_change_view_desc), tint = PathyColors.Primary)
            }
            BackHandler { showExitDialog = true }
        }

        NavigationBottomPanel(
            timeText = formatDuration(recordingStats.duration),
            distanceText = formatDistance(context, recordingStats.distance.toDouble()),
            isNavigating = isRecording,
            movementMode = movementMode,
            onModeChange = {
                if (!isRecording) movementMode = it
                else Toast.makeText(context, context.getString(R.string.nav_mode_locked), Toast.LENGTH_SHORT).show()
            },
            gpsActive = true,
            onNavigateClick = {
                when {
                    !isLocationEnabled(context) -> showLocationDialog = true
                    isRecording -> RecordingController.stop(context)
                    else -> RecordingController.start(context, sportFor(movementMode))
                }
            },
            onReportClick = { showReportSheet = true },
            startLabel = stringResource(R.string.nav_record_start),
            stopLabel = stringResource(R.string.nav_record_stop)
        )

        ReportDialogsAndSheets(
            context = context,
            location = lastLocation,
            showReportSheet = showReportSheet,
            openHazardReportDialog = openHazardReportDialog,
            openPointOfInterestDialog = openPointOfInterestDialog,
            onDismissReportSheet = { showReportSheet = false },
            onDismissHazard = { openHazardReportDialog = false },
            onDismissPoi = { openPointOfInterestDialog = false },
            onOpenHazardDialog = { openHazardReportDialog = true },
            onOpenPoiDialog = { openPointOfInterestDialog = true },
            onOptionSelected = { report ->
                drawReportMarker(context, mapView, report)
                if (FirebaseAuth.getInstance().currentUser != null) showReward = true
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            text = { Text(stringResource(R.string.nav_stop_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    // Leaving ends the walk: it is saved, not abandoned.
                    if (isRecording) RecordingController.stop(context)
                    (context as? Activity)?.finish()
                }) {
                    Text(stringResource(R.string.nav_stop_and_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.nav_continue))
                }
            }
        )
    }
    if (showReward) {
        ReportPointsAnimation(visible = showReward, onDone = { showReward = false }, modifier = Modifier)
    }
    if (showLocationDialog) {
        LocationDisabledDialog { showLocationDialog = false }
    }
}

private const val MAX_USEFUL_ACCURACY_M = 80f

private fun sportFor(mode: MovementMode): SportType = when (mode) {
    MovementMode.WALKING -> SportType.HIKING
    MovementMode.BICYCLE -> SportType.CYCLING
    MovementMode.DRIVING -> SportType.OFFROAD
}

/** The banner at the top: the next instruction, how far is left, and a bar showing progress along the trail. */
@Composable
fun NavigationInstructionBox(
    guidance: Guidance,
    progress: Float?,
    offRoute: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = if (offRoute) {
        listOf(PathyColors.Error, PathyColors.Error.copy(alpha = 0.85f))
    } else {
        listOf(PathyColors.AppBar, PathyColors.PrimaryDark)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors))
            .padding(top = 16.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuideIconView(guidance.icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = guidance.text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                guidance.detail?.let {
                    Text(text = it, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun GuideIconView(icon: GuideIcon) {
    val modifier = Modifier.size(36.dp)
    when (icon) {
        // The turn drawables show a physical direction, so they are never mirrored for right-to-left languages.
        GuideIcon.LEFT -> Icon(painterResource(R.drawable.ic_turn_left), null, modifier, Color.White)
        GuideIcon.RIGHT -> Icon(painterResource(R.drawable.ic_turn_right), null, modifier, Color.White)
        GuideIcon.STRAIGHT -> Icon(painterResource(R.drawable.ic_straight), null, modifier, Color.White)
        GuideIcon.ARRIVED -> Icon(painterResource(R.drawable.ic_arrived), null, modifier, Color.White)
        GuideIcon.U_TURN -> Icon(Icons.Filled.UTurnLeft, null, modifier, Color.White)
        GuideIcon.OFF_ROUTE -> Icon(Icons.Filled.Warning, null, modifier, Color.White)
        GuideIcon.TO_START -> Icon(Icons.Filled.Flag, null, modifier, Color.White)
        GuideIcon.LOCATING -> Icon(Icons.Filled.MyLocation, null, modifier, Color.White)
    }
}
