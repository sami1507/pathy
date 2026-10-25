package com.example.pathy.screens

import com.example.pathy.i18n.applyAppLanguage
import com.example.pathy.i18n.tagLabel
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.theme.MapPalette

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.data.AuthViewModel
import com.example.pathy.ReportDialogsAndSheets
import com.example.pathy.SignInActivity
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.SavedTrail
import com.example.pathy.drawReportMarker
import com.example.pathy.getMiddlePointLocationName
import com.example.pathy.pointToAndroidLocation
import com.example.pathy.uploadTrailToFirestore
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.LogoPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.pathy.CreatePostActivity
import com.example.pathy.CreateEventActivity
import com.example.pathy.LocationDisabledDialog
import com.example.pathy.convertToLatLngDTO
import com.example.pathy.data.TrailTags
import com.example.pathy.formatDistance
import com.example.pathy.formatDuration
import com.example.pathy.isLocationEnabled
import com.example.pathy.ui.theme.PathyColors
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.example.pathy.CreateGroupActivity
import com.example.pathy.FindNearbyGroupsActivity
import com.example.pathy.GroupJoinOptionsDialog
import com.example.pathy.NavigationTypeDialog
import com.example.pathy.PitchDialog
import com.example.pathy.ReportPointsAnimation
import com.example.pathy.WalkieTalkieActivity
import com.example.pathy.trailClimb
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.MovementMode
import com.example.pathy.needVoiceRuntimePerms
import com.example.pathy.requestVoicePermissions
import com.example.pathy.walkietalkie.TransportCoordinator
import com.example.pathy.walkietalkie.WalkieTalkieService
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("MissingPermission", "ImplicitSamInstance")
@Composable
fun NavigationScreen(
    lat: Double,
    lng: Double,
    groupId: String? = null,
    fromGroups: Boolean = false
) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val mapView = remember { MapView(context) }
    val mapboxMap = mapView.
    getMapboxMap()
    val coroutineScope = rememberCoroutineScope()
    var showPitchDialog by remember { mutableStateOf(false) }
    var pitchValue by remember { mutableStateOf(0.0) }
    var movementMode by remember { mutableStateOf(MovementMode.WALKING) }
    var lastLocation by remember { mutableStateOf<Point?>(null) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationCallback = remember { mutableStateOf<LocationCallback?>(null) }
    val routePoints = remember { mutableStateListOf<Point>() }
    val pendingPoints = remember { mutableStateListOf<Point>() }
    val userLocation = Point.fromLngLat(lng, lat)
    val gpsActive by remember { mutableStateOf(true) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var isCameraTrackingUser by remember { mutableStateOf(true) }
    var isCountdownRunning by remember { mutableStateOf(false) }
    var countdownTime by remember { mutableStateOf(5) }
    var startTime by remember { mutableStateOf<Long?>(null) }
    var elapsedTime by remember { mutableStateOf(0L) }
    var totalDistance by remember { mutableStateOf(0.0) }
    var showStopConfirmationDialog by remember { mutableStateOf(false) }
    var showPostEventPopup by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var savedTrailId by remember { mutableStateOf<String?>(null) }
    var trailName by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf("EASY") }
    var rating by remember { mutableStateOf(0f) }
    var showReportSheet by remember { mutableStateOf(false) }
    var openHazardReportDialog by remember { mutableStateOf(false) }
    var openPointOfInterestDialog by remember { mutableStateOf(false) }
    var showReward by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showNavigationTypeDialog by remember { mutableStateOf(false) }
    var isGroupMode by remember { mutableStateOf(false) }
    var isInGroup by remember { mutableStateOf(false) }
    val micEnabled = TransportCoordinator.isMicEnabled
    var showGroupJoinOptionsDialog by remember { mutableStateOf(false) }
    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted && isGroupMode && isInGroup && !groupId.isNullOrBlank() && !TransportCoordinator.isActive) {
            val userName = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: Firebase.auth.currentUser?.email?.substringBefore("@")
                ?: context.getString(R.string.default_user_numbered, (100..999).random().toString())
            TransportCoordinator.start(
                context = context,
                userName = userName,
                groupId = groupId.trim(),
                isHost = false,
                onSpeakerChanged = {},
                onError = {}
            )
        }
    }
    fun drawRoute() {
        val lineString = LineString.fromLngLats(routePoints)
        mapboxMap.getStyle { style ->
            if (style.getSourceAs<GeoJsonSource>("route-source") == null) {
                style.addSource(geoJsonSource("route-source") {
                    geometry(lineString)
                })
                style.addLayer(lineLayer("route-border-layer", "route-source") {
                    lineColor(MapPalette.routeCasingHex)
                    lineWidth(16.0)
                })
                style.addLayer(lineLayer("route-layer", "route-source") {
                    lineColor(MapPalette.routeLineHex)
                    lineWidth(10.0)
                })
            } else {
                style.getSourceAs<GeoJsonSource>("route-source")?.geometry(lineString)
            }
        }
    }
    LaunchedEffect(isGroupMode, isInGroup, groupId) {
        if (isGroupMode && isInGroup && !groupId.isNullOrBlank() && !TransportCoordinator.isActive) {
            if (needVoiceRuntimePerms(context)) {
                requestVoicePermissions(requestPermsLauncher)
            } else {
                val userName = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: Firebase.auth.currentUser?.email?.substringBefore("@")
                    ?: context.getString(R.string.default_user_numbered, (100..999).random().toString())
                TransportCoordinator.start(
                    context = context,
                    userName = userName,
                    groupId = groupId.trim(),
                    isHost = false,
                    onSpeakerChanged = {},
                    onError = {}
                )
            }
        }
    }
    fun clearTrailDialogInputs() {
        trailName = ""
        rating = 0f
        selectedDifficulty = DifficultyLevel.EASY.name
        routePoints.clear()
        showStats = false
        elapsedTime = 0L
        totalDistance = 0.0}

    fun clearRouteFromMap() {
        mapboxMap.getStyle { style ->
            style.removeStyleLayer("route-layer")
            style.removeStyleLayer("route-border-layer")
            style.removeStyleSource("route-source")
        }
    }

    LaunchedEffect(isNavigating) {
        while (isNavigating) {
            delay(3000)
            if (pendingPoints.size >= 2) {
                routePoints.addAll(pendingPoints)
                pendingPoints.clear()
                drawRoute()
            }
        }
    }

    LaunchedEffect(groupId, fromGroups) {
        if (fromGroups && !groupId.isNullOrBlank()) {
            isGroupMode = true
            isInGroup = true
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000L
        ).setMinUpdateIntervalMillis(2000L).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    if (point.latitude() == 0.0 && point.longitude() == 0.0) return
                    val lastPoint = routePoints.lastOrNull()
                    if (lastPoint == null) {
                        pendingPoints.add(point)
                        mapboxMap.setCamera(CameraOptions.Builder().center(point).build())
                    } else {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            lastPoint.latitude(), lastPoint.longitude(),
                            point.latitude(), point.longitude(),
                            results
                        )
                        val distance = results[0]
                        val secondsBetween = 3
                        val speed = (distance / 1000.0) / (secondsBetween / 3600.0)

                        val (minDist, maxSpeed) = when (movementMode) {
                            MovementMode.WALKING  -> 3.0 to 15.0
                            MovementMode.BICYCLE  -> 5.0 to 100.0
                            MovementMode.DRIVING  -> 5.0 to 200.0
                        }

                        if (distance in minDist..500.0 && speed <= maxSpeed) {
                            totalDistance += distance
                            pendingPoints.add(point)
                        }
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            ContextCompat.getMainExecutor(context),
            callback
        )

        locationCallback.value = callback
    }

    fun startSoloNavigation() {
        coroutineScope.launch {
            authViewModel.checkUserAndConnection(
                context = context,
                onAllowed = {
                    if (!isNavigating && !isCountdownRunning) {
                        isCountdownRunning = true
                        coroutineScope.launch {
                            for (i in 3 downTo 1) {
                                countdownTime = i
                                delay(1000)
                            }
                            isCountdownRunning = false
                            isNavigating = true
                            startLocationUpdates()
                            startTime = System.currentTimeMillis()
                            coroutineScope.launch {
                                while (isNavigating) {
                                    elapsedTime = System.currentTimeMillis() - (startTime ?: 0)
                                    delay(1000)
                                }
                            }
                        }
                    } else if (isNavigating) {
                        showStopConfirmationDialog = true
                    }
                },
                onRedirectToLogin = {
                    val intent = Intent(context, SignInActivity::class.java)
                    context.startActivity(intent)
                },
                onContinueWithWarning = {
                    Toast.makeText(context, context.getString(R.string.nav_unstable_connection), Toast.LENGTH_SHORT).show()
                    if (!isNavigating && !isCountdownRunning) {
                        isCountdownRunning = true
                        coroutineScope.launch {
                            for (i in 3 downTo 1) {
                                countdownTime = i
                                delay(1000)
                            }
                            isCountdownRunning = false
                            isNavigating = true
                            startLocationUpdates()
                            startTime = System.currentTimeMillis()
                            coroutineScope.launch {
                                while (isNavigating) {
                                    elapsedTime = System.currentTimeMillis() - (startTime ?: 0)
                                    delay(1000)
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    fun startGroupNavigation() {
        isGroupMode = true
        startSoloNavigation()
    }

    fun startNavigationFlow() {
        if (isInGroup) {
            startGroupNavigation()
        } else {
            showNavigationTypeDialog = true
        }
    }

    fun stopLocationUpdates() {
        locationCallback.value?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapboxMap().loadStyleUri(Style.STANDARD) { style ->
                        style.applyAppLanguage(context)
                        getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)?.updateSettings { enabled = false }
                        getPlugin<LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)?.updateSettings { enabled = false }
                        location.updateSettings {
                            enabled = true
                            pulsingEnabled = true
                            puckBearingEnabled = true
                        }
                        location.addOnIndicatorPositionChangedListener { point ->
                            lastLocation = point
                            if (isNavigating && isCameraTrackingUser) {
                                mapboxMap.setCamera(CameraOptions.Builder().center(point).build())
                            }
                        }
                    }
                    mapView.gestures.addOnMoveListener(object : OnMoveListener {
                        override fun onMoveBegin(detector: MoveGestureDetector) {
                            isCameraTrackingUser = false
                        }

                        override fun onMove(detector: MoveGestureDetector): Boolean = false

                        override fun onMoveEnd(detector: MoveGestureDetector) {}
                    })
                    getMapboxMap().setCamera(CameraOptions.Builder().center(userLocation).zoom(19.0).build())
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = { showPitchDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                .clip(CircleShape)
        ) {
            Icon(Icons.Filled.Terrain, contentDescription = stringResource(R.string.nav_change_view_desc), tint = PathyColors.Primary)
        }

        if (!isCameraTrackingUser) {
            IconButton(
                onClick = {
                    pitchValue = 0.0
                    lastLocation?.let {
                        mapboxMap.flyTo(
                            CameraOptions.Builder()
                                .center(it)
                                .pitch(pitchValue)
                                .zoom(19.0)
                                .build(),
                            mapAnimationOptions {
                                duration(2000L)
                            }
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
        NavigationBottomPanel(
            timeText = formatDuration(elapsedTime),
            distanceText = formatDistance(context, totalDistance),
            isNavigating = isNavigating,
            movementMode = movementMode,
            onModeChange = { movementMode = it },
            gpsActive = gpsActive,
            onNavigateClick = {
                if (!isLocationEnabled(context)) {
                    showLocationDialog = true
                    return@NavigationBottomPanel
                }

                coroutineScope.launch {
                    authViewModel.checkUserAndConnection(
                        context = context,
                        onAllowed = {
                            if (isNavigating) {
                                showStopConfirmationDialog = true
                            } else {
                                startNavigationFlow()
                            }
                        },
                        onRedirectToLogin = {
                            val intent = Intent(context, SignInActivity::class.java)
                            context.startActivity(intent)
                        },
                        onContinueWithWarning = {
                            Toast.makeText(context, context.getString(R.string.nav_unstable_connection), Toast.LENGTH_SHORT).show()
                            if (isNavigating) {
                                showStopConfirmationDialog = true
                            } else {
                                startNavigationFlow()
                            }
                        }
                    )
                }
            },
            onReportClick = { showReportSheet = true }
        )

        if (isCountdownRunning) {
            Text("$countdownTime", style = MaterialTheme.typography.displayLarge, modifier = Modifier.align(
                Alignment.Center))
        }

        if (isNavigating) {
            BackHandler {
                showExitDialog = true
            }
        }

        if (showLocationDialog) {
            LocationDisabledDialog { showLocationDialog = false }
        }

        lastLocation?.let { here ->
            ReportDialogsAndSheets(
                context = context,
                location = pointToAndroidLocation(here),
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
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        showReward = true
                    }
                }
            )
        }

        if(showReward){
            ReportPointsAnimation(
                visible = showReward,
                onDone = {showReward = false},
                modifier = Modifier
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                text = { Text(stringResource(R.string.nav_stop_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        isNavigating = false
                        stopLocationUpdates()
                        showExitDialog = false
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

        if (showStopConfirmationDialog) {
            AlertDialog(
                onDismissRequest = {
                    showStopConfirmationDialog = false
                },
                title = { Text(stringResource(R.string.nav_stop_confirm_title)) },
                confirmButton = {
                    TextButton(onClick = {
                        isNavigating = false
                        stopLocationUpdates()
                        if (routePoints.isNotEmpty()) {
                            showDetailsDialog = true
                            showStopConfirmationDialog = false
                            clearRouteFromMap()
                        } else {
                            Toast.makeText(context, context.getString(R.string.nav_empty_route), Toast.LENGTH_SHORT).show()
                            clearTrailDialogInputs()
                            showStopConfirmationDialog = false
                        }
                    }){
                        Text(stringResource(R.string.common_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showStopConfirmationDialog = false
                    }){
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        if (showPostEventPopup) {
            AlertDialog(
                onDismissRequest = { showPostEventPopup = false },
                confirmButton = {},
                containerColor = MaterialTheme.colorScheme.background,
                title = {
                    Text(
                        stringResource(R.string.nav_route_saved_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = PathyColors.Bark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.nav_share_with_community),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = PathyColors.Bark,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                showPostEventPopup = false
                                savedTrailId?.let { trailId ->
                                    context.startActivity(Intent(context, CreatePostActivity::class.java).apply {
                                        putExtra("trailId", trailId)
                                    })
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryLight),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                stringResource(R.string.nav_create_post),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Button(
                            onClick = {
                                showPostEventPopup = false
                                savedTrailId?.let { trailId ->
                                    context.startActivity(Intent(context, CreateEventActivity::class.java).apply {
                                        putExtra("trailId", trailId)
                                    })
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Warning),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                stringResource(R.string.nav_create_event),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        TextButton(
                            onClick = { showPostEventPopup = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                stringResource(R.string.nav_close),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = PathyColors.Bark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            )
        }
        var trailTags by remember { mutableStateOf(TrailTags()) }

        if (showDetailsDialog) {
            AlertDialog(
                onDismissRequest = {
                    clearTrailDialogInputs()
                    showDetailsDialog = false
                },
                title = { Text(stringResource(R.string.trail_details_header)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        TrailDetailsContent(
                            trailName = trailName,
                            onNameChange = { trailName = it },
                            rating = rating,
                            onRatingChange = { rating = it },
                            selectedDifficulty = selectedDifficulty,
                            onDifficultyChange = { selectedDifficulty = it },
                            geoPoints = convertToLatLngDTO(routePoints),
                            tags = trailTags,
                            onTagsChange = { trailTags = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val id = UUID.randomUUID().toString()
                            val geoPoints = convertToLatLngDTO(routePoints)
                            authViewModel.checkUserAndConnection(
                                context = context,
                                onRedirectToLogin = {
                                    context.startActivity(Intent(context, SignInActivity::class.java))
                                },
                                onContinueWithWarning = {
                                    Toast.makeText(context, context.getString(R.string.nav_no_connection_retry), Toast.LENGTH_LONG).show()
                                },
                                onAllowed = {
                                    savedTrailId = id
                                    val savedTrail = SavedTrail(
                                        id = id,
                                        name = trailName,
                                        difficulty = DifficultyLevel.valueOf(selectedDifficulty),
                                        rating = rating,
                                        distanceInMeters = totalDistance,
                                        durationInMillis = elapsedTime,
                                        geoPoints = geoPoints,
                                        midpoint = geoPoints.getOrNull(geoPoints.size / 2)?.let {
                                            LatLngDTO(it.latitude, it.longitude)
                                        } ?: LatLngDTO(0.0, 0.0),
                                        locationName = getMiddlePointLocationName(context, geoPoints),
                                        createdByUid = Firebase.auth.currentUser?.uid.orEmpty(),
                                        tags = trailTags
                                    )

                                    uploadTrailToFirestore(
                                        context = context,
                                        trail = savedTrail,
                                        onSuccess = {
                                            Toast.makeText(context, context.getString(R.string.trail_saved_success), Toast.LENGTH_SHORT).show()
                                            showDetailsDialog = false
                                            showPostEventPopup = true
                                            clearTrailDialogInputs()
                                        },
                                        onFailure = { errorMessage ->
                                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            )
                        }
                    }) { Text(stringResource(R.string.nav_confirm_and_save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        clearTrailDialogInputs()
                        showDetailsDialog = false
                    }){
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        NavigationTypeDialog(
            show = showNavigationTypeDialog,
            onDismiss = { showNavigationTypeDialog = false },
            onSolo = {
                showNavigationTypeDialog = false
                isGroupMode = false
                startSoloNavigation()
            },
            onGroup = {
                showNavigationTypeDialog = false
                isGroupMode = true
                showGroupJoinOptionsDialog = true
            }
        )

        GroupJoinOptionsDialog(
            show = showGroupJoinOptionsDialog,
            onDismiss = { showGroupJoinOptionsDialog = false },
            onFindNearby = {
                showGroupJoinOptionsDialog = false
                context.startActivity(Intent(context, FindNearbyGroupsActivity::class.java))
            },
            onCreateGroup = {
                showGroupJoinOptionsDialog = false
                context.startActivity(Intent(context, CreateGroupActivity::class.java))
            }
        )

        if (isGroupMode && isInGroup) {
            val panelBottomOffset = 160.dp

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = panelBottomOffset)
                    .zIndex(10f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                IconButton(
                    onClick = {
                        val gid = groupId?.trim().orEmpty()
                        if (gid.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.nav_no_group), Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }

                        val userName = Firebase.auth.currentUser?.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: (Firebase.auth.currentUser?.email?.substringBefore("@"))
                            ?: context.getString(R.string.default_user_numbered, (100..999).random().toString())

                        val intent = Intent(context, WalkieTalkieActivity::class.java).apply {
                            putExtra("extra_group_id", gid)
                            putExtra("extra_is_host", false)
                            putExtra("extra_user_name", userName)
                            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        runCatching { context.startActivity(intent) }
                            .onFailure { e ->
                                android.util.Log.e("NavigationScreen", "Failed to open WalkieTalkieActivity", e)
                                val msg = when (e) {
                                    is android.content.ActivityNotFoundException ->
                                        "WalkieTalkieActivity לא הוגדר ב־AndroidManifest."
                                    else -> context.getString(R.string.nav_group_screen_error)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                        .border(2.dp, PathyColors.PrimaryDark, CircleShape)
                        .shadow(6.dp, shape = CircleShape)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = stringResource(R.string.nav_group_list_desc),
                        tint = PathyColors.PrimaryDark,
                        modifier = Modifier.size(28.dp)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        val next = !TransportCoordinator.isMicEnabled
                        TransportCoordinator.setMicEnabled(next)
                        Toast.makeText(
                            context,
                            if (next) context.getString(R.string.nav_mic_on) else context.getString(R.string.nav_mic_off),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = CircleShape,
                    containerColor = if (micEnabled) PathyColors.Error else Color.DarkGray,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.nav_mic_desc),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationBottomPanel(
    modifier: Modifier = Modifier,
    timeText: String,
    distanceText: String,
    isNavigating: Boolean,
    movementMode: MovementMode,
    onModeChange: (MovementMode) -> Unit,
    gpsActive: Boolean,
    onNavigateClick: () -> Unit,
    onReportClick: () -> Unit,
    startLabel: String = stringResource(R.string.sheet_start_navigation),
    stopLabel: String = stringResource(R.string.nav_stop_button)
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ){
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PathyColors.PrimaryContainer),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏱️ $timeText", fontWeight = FontWeight.Bold)
                        Text("📏 $distanceText", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = onNavigateClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNavigating) PathyColors.Error else Color(
                                    0xFF015B04
                                )
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text(if (isNavigating) stopLabel else startLabel, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onReportClick,
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary),
                            elevation = ButtonDefaults.buttonElevation(10.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Warning, contentDescription = stringResource(R.string.nav_report_desc), tint = Color.White)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MovementModeSelector(
                            isNavigating = isNavigating,
                            selectedMode = movementMode,
                            onModeChange = { onModeChange(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovementModeSelector(
    isNavigating: Boolean,
    selectedMode: MovementMode,
    onModeChange: (MovementMode) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MovementMode.values().forEach { mode ->
            val isSelected = mode == selectedMode
            AssistChip(
                onClick = {
                    if(!isNavigating){
                        onModeChange(mode)
                    }
                    else {
                        Toast.makeText(context, context.getString(R.string.nav_mode_locked), Toast.LENGTH_SHORT).show()
                    }
                },
                label = {
                    Text(
                        when (mode) {
                            MovementMode.WALKING -> "🚶"
                            MovementMode.BICYCLE -> "🚲"
                            MovementMode.DRIVING -> "🚗"
                        },
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = when {
                        isSelected -> PathyColors.Primary
                        isNavigating -> Color.Gray
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier
                    .height(40.dp)
                    .defaultMinSize(minWidth = 20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrailDetailsContent(
    trailName: String,
    onNameChange: (String) -> Unit,
    rating: Float,
    onRatingChange: (Float) -> Unit,
    selectedDifficulty: String,
    onDifficultyChange: (String) -> Unit,
    geoPoints: List<LatLngDTO>,
    tags: TrailTags,
    onTagsChange: (TrailTags) -> Unit
) {
    val suitableOptions = listOf(
        "\uD83D\uDEB6\u200D♂\uFE0Fהליכה  ",
        "\uD83D\uDEB4\u200D♂\uFE0Fאופניים  ",
        "\uD83D\uDE97רכבי שטח  "
    )
    val groupOptions = listOf(
        "❤זוג\uFE0F\t",
        "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66משפחה",
        "\uD83D\uDC76ילדים קטנים"
    )

    LaunchedEffect(geoPoints) {
        if (geoPoints.isNotEmpty() && tags.elevationGain == null) {
            trailClimb(geoPoints)?.let { (gain, max) ->
                onTagsChange(tags.copy(elevationGain = gain, maxElevation = max))
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = trailName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.field_trail_name)) },
            modifier = Modifier.fillMaxWidth()
        )

        Column {
            Text(stringResource(R.string.nav_rate_trail))
            Row {
                for (i in 1..5) {
                    IconButton(onClick = { onRatingChange(i.toFloat()) }) {
                        Icon(
                            imageVector = if (rating >= i) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = if (rating >= i) PathyColors.Highlight else Color.Gray
                        )
                    }
                }
            }
        }

        Column {
            Text(stringResource(R.string.nav_difficulty_label))
            DifficultyLevel.values().forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedDifficulty == level.name,
                        onClick = { onDifficultyChange(level.name) }
                    )
                    Text(level.name)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.nav_more_tags), fontWeight = FontWeight.Bold)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(stringResource(R.string.tag_chip_forest), tags.isForest) { onTagsChange(tags.copy(isForest = it)) }
            FilterChip(stringResource(R.string.tag_chip_water), tags.hasWater) { onTagsChange(tags.copy(hasWater = it)) }
            FilterChip(stringResource(R.string.tag_chip_viewpoint), tags.hasViewpoint) { onTagsChange(tags.copy(hasViewpoint = it)) }
            FilterChip(stringResource(R.string.tag_chip_extreme), tags.hasExtremeSection) { onTagsChange(tags.copy(hasExtremeSection = it)) }
            FilterChip(stringResource(R.string.tag_chip_accessible), tags.isAccessibility) { onTagsChange(tags.copy(isAccessibility = it)) }
        }

        Text(stringResource(R.string.nav_suitable_for), fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suitableOptions.forEach { option ->
                val selected = tags.suitableFor.contains(option)
                FilterChip(tagLabel(option), selected) {
                    val updated = if (it) tags.suitableFor + option else tags.suitableFor - option
                    onTagsChange(tags.copy(suitableFor = updated))
                }
            }
        }

        Text(stringResource(R.string.nav_suitable_for), fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupOptions.forEach { option ->
                val selected = tags.suggestedGroupKind.contains(option)
                FilterChip(tagLabel(option), selected) {
                    val updated = if (it) tags.suggestedGroupKind + option else tags.suggestedGroupKind - option
                    onTagsChange(tags.copy(suggestedGroupKind = updated))
                }
            }
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: (Boolean) -> Unit) {
    AssistChip(
        onClick = { onClick(!selected) },
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) PathyColors.Primary else MaterialTheme.colorScheme.outlineVariant,
            labelColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp)
    )
}