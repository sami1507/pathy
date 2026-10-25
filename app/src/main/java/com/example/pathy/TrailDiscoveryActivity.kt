package com.example.pathy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.screens.TrailDiscoveryScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Trail discovery: near the phone, or around any place in the world the person searches for.
 * Finding the phone's position is only one way in; if it fails the screen says so and the search works anyway.
 */
class TrailDiscoveryActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                DiscoveryRoute()
            }
        }
    }
}

/** The last known position if there is one (instant), otherwise a fresh fix. Null when neither is available. */
@SuppressLint("MissingPermission")
private suspend fun findPosition(context: Context): Point? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return try {
        val location = client.lastLocation.await()
            ?: client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        location?.let { Point.fromLngLat(it.longitude, it.latitude) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun DiscoveryRoute() {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Point?>(null) }
    var locationFailed by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var attempt by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Try a few times: a phone that has just woken up often has no fix for the first seconds.
    LaunchedEffect(granted, attempt) {
        if (!granted) {
            locationFailed = true
            return@LaunchedEffect
        }
        locationFailed = false
        repeat(4) {
            findPosition(context)?.let { userLocation = it; return@LaunchedEffect }
            delay(2_500)
        }
        locationFailed = true
    }

    TrailDiscoveryScreen(
        userLocation = userLocation,
        locationFailed = locationFailed && userLocation == null,
        onRetryLocation = { if (!granted) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) else attempt++ }
    )
}
