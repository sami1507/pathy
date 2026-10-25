package com.example.pathy

import kotlin.math.*
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import java.io.File
import java.util.*

fun hasOfflineTrails(context: Context): Boolean {
    val trailsDir = File(context.filesDir, "trails")
    return trailsDir.exists() && trailsDir.listFiles()?.any { it.isDirectory } == true
}

fun requestLocationPrompt(
    context: Context,
    launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
) {
    val locationRequest = LocationRequest.create().apply {
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    val builder = LocationSettingsRequest.Builder()
        .addLocationRequest(locationRequest)
        .setAlwaysShow(true)

    val settingsClient = LocationServices.getSettingsClient(context)
    val task = settingsClient.checkLocationSettings(builder.build())

    task.addOnFailureListener { exception ->
        if (exception is ResolvableApiException) {
            try {
                val intentSenderRequest =
                    IntentSenderRequest.Builder(exception.resolution).build()
                launcher.launch(intentSenderRequest)
            } catch (sendEx: IntentSender.SendIntentException) {
                android.util.Log.w("PATHY", "Could not show the location settings prompt", sendEx)
            }
        }
    }
}

private fun requiredNearbyPerms(): Array<String> {
    val perms = mutableListOf(
        android.Manifest.permission.RECORD_AUDIO
    )
    perms += android.Manifest.permission.ACCESS_COARSE_LOCATION
    perms += android.Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= 33) {
        perms += android.Manifest.permission.NEARBY_WIFI_DEVICES
        perms += android.Manifest.permission.BLUETOOTH_SCAN
        perms += android.Manifest.permission.BLUETOOTH_CONNECT
    } else if (Build.VERSION.SDK_INT >= 31) {
        perms += android.Manifest.permission.BLUETOOTH_SCAN
        perms += android.Manifest.permission.BLUETOOTH_CONNECT
    }
    return perms.toTypedArray()
}

fun requestVoicePermissions(launcher: ActivityResultLauncher<Array<String>>) {
    launcher.launch(requiredNearbyPerms())
}

fun needVoiceRuntimePerms(context: Context): Boolean {
    fun has(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    return requiredNearbyPerms().any { !has(it) }
}


fun isLocationEnabled(context: Context): Boolean {
    val locationMode: Int = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.LOCATION_MODE,
        Settings.Secure.LOCATION_MODE_OFF
    )
    return locationMode != Settings.Secure.LOCATION_MODE_OFF
}

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
