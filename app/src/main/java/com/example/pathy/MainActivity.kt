package com.example.pathy

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.applyAppLanguage
import androidx.compose.ui.res.stringResource
import com.example.pathy.i18n.PathyActivity
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pathy.components.ScoutFloatingButton
import com.example.pathy.data.FancyDrawerMenu
import com.example.pathy.data.LocationViewModel
import com.example.pathy.data.NearbyTrailsHolder
import com.example.pathy.ui.screens.NavigationActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.animation.MapAnimationOptions.Companion.mapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.logo.LogoPlugin
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.google.firebase.FirebaseApp
import com.example.pathy.data.Report
import com.example.pathy.data.TrailData
import com.example.pathy.screens.fetchTrails
import com.example.pathy.offline.OfflineRegions
import com.example.pathy.offline.OfflineTrails
import com.example.pathy.screens.TrailListCard
import com.example.pathy.screens.toCardModel
import com.example.pathy.ui.map.HomeTrailLayers
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jakewharton.threetenabp.AndroidThreeTen
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : PathyActivity() {

    private val locationViewModel: LocationViewModel by viewModels()
    private var lastLocation: Point? = Point.fromLngLat(-87.6298, 41.8781) // שיקגו
    private val reportMarkers = mutableListOf<Report>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidThreeTen.init(this)
        FirebaseApp.initializeApp(this)
        setPathyContent {
            MapWithUserLocation()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MapWithUserLocation() {
        val context = LocalContext.current
        val locationPermissionGranted = remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            locationPermissionGranted.value = isGranted
            if (!isGranted) {
                Toast.makeText(context, context.getString(R.string.map_location_permission_needed), Toast.LENGTH_LONG)
                    .show()
            }
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val isEnabled = isLocationEnabled(context)
            if (!isEnabled) {
                Toast.makeText(context, context.getString(R.string.location_services_off), Toast.LENGTH_SHORT).show()
            }
        }
        val showPopup = remember { mutableStateOf(false) }
        val trailId = remember { mutableStateOf<String?>(null) }
        val sharedBy = remember { mutableStateOf<String?>(null) }
        val receivedTrail = remember { mutableStateOf<TrailData?>(null) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var showStyleDialog by remember { mutableStateOf(false) }
        // Outdoors is the style that shows paths, contours and terrain, and the one trails are saved in for offline use.
        var selectedStyle by remember { mutableStateOf(OfflineRegions.STYLE) }
        val mapViewRef = remember { mutableStateOf<MapView?>(null) }
        var showPitchDialog by remember { mutableStateOf(false) }
        var pitchValue by remember { mutableStateOf(0.0) }
        var showReportSheet by remember { mutableStateOf(false) }
        var showLocationDialog by remember { mutableStateOf(false) }
        var isFlyToFinished by remember { mutableStateOf(false) }
        var isCameraMoved by remember { mutableStateOf(false) }
        var openHazardReportDialog by remember { mutableStateOf(false) }
        var openPointOfInterestDialog by remember { mutableStateOf(false) }
        var selectedReport by remember { mutableStateOf<Report?>(null) }
        var showReward by remember { mutableStateOf(false) }

        // Natural trails on the map: the ones around the person, the ones saved on the phone (they still
        // show with no connection), and the ones around a place the person moved the map to.
        val online by com.example.pathy.offline.rememberIsOnline()
        val nearby by NearbyTrailsHolder.trails.collectAsState()
        var saved by remember { mutableStateOf<List<TrailData>>(emptyList()) }
        var areaTrails by remember { mutableStateOf<List<TrailData>>(emptyList()) }
        var areaCenter by remember { mutableStateOf<Point?>(null) }
        var areaFetchedAt by remember { mutableStateOf<Point?>(null) }
        var areaLoading by remember { mutableStateOf(false) }
        var selectedTrail by remember { mutableStateOf<TrailData?>(null) }
        var styleVersion by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            saved = withContext(Dispatchers.IO) { OfflineTrails.list(OfflineTrails.root(context)).map { it.trail } }
        }
        val savedIds = remember(saved) { saved.map { it.id }.toSet() }
        val trailsOnMap = remember(nearby, areaTrails, saved) {
            ((nearby + areaTrails).filter { it.isHikingTrail } + saved).distinctBy { it.id }
        }
        val trailsNow by rememberUpdatedState(trailsOnMap)
        LaunchedEffect(trailsOnMap, styleVersion) {
            mapViewRef.value?.getMapboxMap()?.getStyle()?.let { HomeTrailLayers.update(it, trailsOnMap) }
        }
        LaunchedEffect(selectedTrail, styleVersion) {
            mapViewRef.value?.getMapboxMap()?.getStyle()?.let { HomeTrailLayers.select(it, selectedTrail) }
        }

        LaunchedEffect(Unit) {
            if (!isLocationEnabled(context)) {
                showLocationDialog = true
            }
        }

        LaunchedEffect(Unit) {
            if (!locationPermissionGranted.value) {
                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        LaunchedEffect(selectedStyle) {
            mapViewRef.value?.getMapboxMap()?.loadStyleUri(selectedStyle) { style ->
                style.applyAppLanguage(context)
                mapViewRef.value?.let {
                    setupMapStyle(
                        style,
                        it,
                        isFlyToFinished = { isFlyToFinished = true })
                }
                // A new style starts without our layers, so the trails are drawn onto it again.
                styleVersion++
            }
        }

        LaunchedEffect(Unit) {
            val data = (context as Activity).intent?.data
            val sharedByUid = data?.getQueryParameter("sharedBy")
            val id = data?.getQueryParameter("trailId")

            if (!sharedByUid.isNullOrEmpty() && !id.isNullOrEmpty()) {
                sharedBy.value = sharedByUid
                trailId.value = id
                showPopup.value = true

                val db = FirebaseFirestore.getInstance()
                db.collection("trails").document(id).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            try {
                                val trail = document.toObject(TrailData::class.java)
                                if (trail != null) {
                                    receivedTrail.value = trail
                                    showPopup.value = true
                                } else {
                                    Toast.makeText(context, context.getString(R.string.trail_load_error), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.conversion_error, e.message), Toast.LENGTH_LONG).show()
                                android.util.Log.w("PATHY", "Operation failed", e)
                            }
                        }
                    }
            }
        }


        if (showLocationDialog) {
            LocationPromptDialog(
                onEnableClick = {
                    requestLocationPrompt(context, launcher)
                    showLocationDialog = false
                },
                onDismiss = {
                    val isEnabled = isLocationEnabled(context)
                    if (!isEnabled) {
                        Toast.makeText(context, context.getString(R.string.location_services_off), Toast.LENGTH_SHORT).show()
                    }
                    showLocationDialog = false
                }
            )
        }
        if (!showLocationDialog && showPopup.value && receivedTrail.value != null && sharedBy.value != null) {
            SharedTrailPopup(
                sharedByUid = sharedBy.value!!,
                trailId = trailId.value!!,
                onDismiss = { showPopup.value = false },
                onViewTrail = { trailId ->
                    val trailIntent = TrailDetailsActivity.createIntent(context, receivedTrail.value!!)
                    context.startActivity(trailIntent)
                }
            )
        }

        Box(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Pathy",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_desc), tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showPitchDialog = true },
                                modifier = Modifier
                                .padding(top = 6.dp, end = 6.dp)
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                                .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Terrain,
                                    contentDescription = stringResource(R.string.nav_change_view_desc),
                                    tint = PathyColors.Primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = PathyColors.AppBar
                        )
                    )
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = false,
                        drawerContent = {
                            FancyDrawerMenu(
                                drawerState = drawerState,
                                scope = scope,
                                context = context,
                                onOpenMapStyleDialog = { showStyleDialog = true }
                            )
                        }
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = {
                                    MapView(context).apply {
                                        mapViewRef.value = this
                                        // A short scale bar: the default one ran under the trails chip at the top.
                                        getPlugin<com.mapbox.maps.plugin.scalebar.ScaleBarPlugin>(
                                            com.mapbox.maps.plugin.Plugin.MAPBOX_SCALEBAR_PLUGIN_ID
                                        )?.updateSettings { ratio = 0.22f }
                                        getMapboxMap().setCamera(
                                            CameraOptions.Builder()
                                                .center(lastLocation)
                                                .zoom(0.5)
                                                .build()
                                        )
                                        getMapboxMap().loadStyleUri(selectedStyle) {
                                            it.applyAppLanguage(context)
                                            setupMapStyle(
                                                it,
                                                this,
                                                isFlyToFinished = { isFlyToFinished = true })
                                            styleVersion++
                                        }
                                        gestures.addOnMoveListener(object : OnMoveListener {
                                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                                isCameraMoved = true
                                            }

                                            override fun onMove(detector: MoveGestureDetector): Boolean = false

                                            override fun onMoveEnd(detector: MoveGestureDetector) {
                                                // Far from where trails were last looked up: offer to look here too.
                                                val here = getMapboxMap().cameraState.center
                                                val reference = areaFetchedAt ?: lastTrailFetch
                                                areaCenter = if (reference == null || here.distanceTo(reference) > 2_000.0) here else null
                                            }
                                        })
                                        // Touching a trail opens its card; touching anywhere else closes it.
                                        gestures.addOnMapClickListener(object : OnMapClickListener {
                                            override fun onMapClick(point: Point): Boolean {
                                                HomeTrailLayers.idAt(getMapboxMap(), point) { id ->
                                                    selectedTrail = id?.let { wanted -> trailsNow.firstOrNull { it.id == wanted } }
                                                }
                                                return false
                                            }
                                        })

                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (trailsOnMap.isNotEmpty()) {
                                    MapChip(
                                        icon = Icons.Filled.Hiking,
                                        text = stringResource(R.string.map_trails_nearby_chip, trailsOnMap.size),
                                        onClick = { context.startActivity(Intent(context, TrailDiscoveryActivity::class.java)) }
                                    )
                                }
                                // Looking up trails in another place needs a connection, so it is only offered with one.
                                areaCenter?.takeIf { online }?.let { center ->
                                    MapChip(
                                        icon = Icons.Filled.Search,
                                        text = stringResource(if (areaLoading) R.string.map_searching else R.string.map_search_area),
                                        onClick = {
                                            if (!areaLoading) {
                                                areaLoading = true
                                                scope.launch {
                                                    try {
                                                        areaTrails = fetchTrails(center)
                                                        areaFetchedAt = center
                                                        areaCenter = null
                                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, context.getString(R.string.discover_load_failed), Toast.LENGTH_LONG).show()
                                                    } finally {
                                                        areaLoading = false
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            if (isCameraMoved) {
                                IconButton(
                                    onClick = {
                                        pitchValue = 0.0
                                        lastLocation?.let {
                                            mapViewRef.value?.getMapboxMap()?.flyTo(
                                                CameraOptions.Builder()
                                                    .center(it)
                                                    .pitch(pitchValue)
                                                    .zoom(14.5)
                                                    .build(),
                                                mapAnimationOptions {
                                                    duration(2000L)
                                                }
                                            )
                                            isCameraMoved = false
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
                            Button(
                                onClick = { showReportSheet = true },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 20.dp, bottom = 32.dp)
                                    .height(56.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary),
                                elevation = ButtonDefaults.buttonElevation(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = stringResource(R.string.nav_report_desc),
                                    tint = Color.White
                                )
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(context, NavigationActivity::class.java).apply {
                                        putExtra("lat", lastLocation!!.latitude())
                                        putExtra("lng", lastLocation!!.longitude())
                                    }
                                    val options = ActivityOptions.makeCustomAnimation(
                                        context as Activity,
                                        android.R.anim.fade_in,
                                        android.R.anim.fade_out
                                    )
                                    context.startActivity(intent, options.toBundle())
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp)
                                    .height(56.dp),
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryDark),
                                elevation = ButtonDefaults.buttonElevation(10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Navigation,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.main_record_hike), color = Color.White)
                            }

                            ScoutFloatingButton(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 20.dp, bottom = 120.dp),
                                onScoutClick = {
                                    context.startActivity(Intent(context, ScoutActivity::class.java))
                                }
                            )
                            selectedTrail?.let { trail ->
                                val mid = trail.midpoint
                                val away = lastLocation?.let { me -> mid?.let { Point.fromLngLat(it.longitude, it.latitude).distanceTo(me) } }
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp).padding(bottom = 104.dp)
                                ) {
                                    TrailListCard(
                                        model = trail.toCardModel(awayMeters = away),
                                        onClick = { context.startActivity(TrailDetailsActivity.createIntent(context, trail)) },
                                        offline = trail.id in savedIds,
                                        trailing = {
                                            IconButton(onClick = { selectedTrail = null }, modifier = Modifier.size(48.dp)) {
                                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                                            }
                                        }
                                    )
                                }
                            }
                            ReportDialogsAndSheets(
                                context = context,
                                location = lastLocation?.let { pointToAndroidLocation(it) },
                                showReportSheet = showReportSheet,
                                openHazardReportDialog = openHazardReportDialog,
                                openPointOfInterestDialog = openPointOfInterestDialog,
                                onDismissReportSheet = { showReportSheet = false },
                                onDismissHazard = { openHazardReportDialog = false },
                                onDismissPoi = { openPointOfInterestDialog = false },
                                onOpenHazardDialog = { openHazardReportDialog = true },
                                onOpenPoiDialog = { openPointOfInterestDialog = true },
                                onOptionSelected = { report ->
                                    selectedReport = report
                                    reportMarkers.add(report)

                                    mapViewRef.value?.let { mapView ->
                                        drawReportMarker(context, mapView, report)
                                    }
                                    if(FirebaseAuth.getInstance().currentUser!=null){
                                        showReward = true
                                    }
                                }
                            )

                            if(showReward){
                                ReportPointsAnimation(
                                    visible = showReward,
                                    onDone = {showReward = false},
                                    modifier = Modifier
                                )
                            }

                            PitchDialog(
                                showDialog = showPitchDialog,
                                onDismiss = { showPitchDialog = false },
                                pitchValue = pitchValue,
                                onPitchChange = { pitchValue = it },
                                mapView = mapViewRef.value
                            )

                            if (showStyleDialog) {
                                Dialog(onDismissRequest = { showStyleDialog = false }) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = PathyColors.AppBar,
                                        tonalElevation = 12.dp,
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(stringResource(R.string.map_style_title), style = MaterialTheme.typography.titleLarge, color = Color.White)

                                            MapStyleOption(stringResource(R.string.map_style_standard), "🗺️", Style.STANDARD) {
                                                selectedStyle = it
                                                showStyleDialog = false
                                            }
                                            MapStyleOption(stringResource(R.string.map_style_satellite), "🛰️", Style.SATELLITE) {
                                                selectedStyle = it
                                                showStyleDialog = false
                                            }
                                            MapStyleOption(stringResource(R.string.map_style_dark), "🌑", Style.DARK) {
                                                selectedStyle = it
                                                showStyleDialog = false
                                            }
                                            MapStyleOption(stringResource(R.string.map_style_light), "🌞", Style.LIGHT) {
                                                selectedStyle = it
                                                showStyleDialog = false
                                            }
                                            MapStyleOption(stringResource(R.string.map_style_outdoors), "⛰️", Style.OUTDOORS) {
                                                selectedStyle = it
                                                showStyleDialog = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    @Composable
    fun MapStyleOption(label: String, icon: String, styleUri: String, onClick: (String) -> Unit) {
        Card(
            onClick = { onClick(styleUri) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }

    /** The map and callback the position listener works with; the listener itself is registered once. */
    private var positionMap: MapView? = null
    private var onFirstFlight: (() -> Unit)? = null
    private var hasFix = false
    private var lastTrailFetch: Point? = null

    private val positionListener = OnIndicatorPositionChangedListener { point -> onPosition(point) }

    private fun setupMapStyle(style: Style, mapView: MapView, isFlyToFinished: () -> Unit)
    {
        val attributionPlugin = mapView.getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)
        attributionPlugin?.updateSettings { enabled = false }

        val logoPlugin = mapView.getPlugin<LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)
        logoPlugin?.updateSettings { enabled = false }

        val locationComponent = mapView.location
        locationComponent.updateSettings {
            enabled = true
            pulsingEnabled = true
            puckBearingEnabled = true
        }

        // This runs once at start-up and again whenever the map style changes. A listener added each time
        // piled up, so every move was handled several times.
        positionMap = mapView
        onFirstFlight = isFlyToFinished
        locationComponent.removeOnIndicatorPositionChangedListener(positionListener)
        locationComponent.addOnIndicatorPositionChangedListener(positionListener)
    }

    private fun onPosition(point: Point) {
        val previous = lastLocation
        if (!hasFix || previous == null || point.distanceTo(previous) > 5.0) {
            lastLocation = point
            locationViewModel.updateLocation(point)
        }

        // Nearby trails are fetched when the person has moved a good way, not at every step.
        val fetchedAt = lastTrailFetch
        if (fetchedAt == null || point.distanceTo(fetchedAt) > TRAIL_REFRESH_M) {
            lastTrailFetch = point
            lifecycleScope.launch {
                try {
                    val trailsFromApi = fetchTrails(point)
                    val trailsFromFirestore = fetchFirestoreTrailsNearby(point)
                    NearbyTrailsHolder.update((trailsFromApi + trailsFromFirestore).distinctBy { it.id })
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline or a server problem: the map stays usable, the trail list just stays as it was.
                    lastTrailFetch = null
                }
            }
        }

        // Fly to the person once, when the first position arrives. Doing it at every step took the map
        // away from anyone who was looking at another part of it.
        if (!hasFix) {
            hasFix = true
            val map = positionMap ?: return
            map.postDelayed({
                map.getMapboxMap().flyTo(
                    CameraOptions.Builder().center(point).zoom(14.5).build(),
                    mapAnimationOptions { duration(3000L) }
                )
                map.postDelayed({ onFirstFlight?.invoke() }, 3000L)
            }, 500)
        }
    }

    private companion object {
        const val TRAIL_REFRESH_M = 400.0
    }
}

/** A small button floating on the map: the number of trails around, or "search this area". */
@Composable
private fun MapChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PathyColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
