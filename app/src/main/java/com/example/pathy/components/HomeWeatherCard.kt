package com.example.pathy.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pathy.R
import com.example.pathy.weather.OutdoorWeatherService
import com.example.pathy.weather.WeatherEvaluation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Location is requested only after the user chooses to view local weather. */
@Composable
fun HomeWeatherCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var weather by remember { mutableStateOf<WeatherEvaluation?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val fetch: () -> Unit = {
        scope.launch {
            loading = true
            failed = false
            weather = null
            val cancellation = CancellationTokenSource()
            try {
                val location = LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token).await()
                if (location == null) failed = true else {
                    val result = OutdoorWeatherService().getWeatherEvaluation(location.latitude, location.longitude)
                    failed = result.isError
                    if (!result.isError) weather = result
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { failed = true
            } finally { cancellation.cancel(); loading = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) fetch() else failed = true
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.weather_title), style = MaterialTheme.typography.titleMedium)
            if (loading) CircularProgressIndicator()
            weather?.let { Text(stringResource(R.string.weather_value, it.temperature, it.windSpeed)) }
            if (failed) Text(stringResource(R.string.weather_unavailable))
            TextButton(enabled = !loading, onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetch()
                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Text(stringResource(R.string.weather_action)) }
        }
    }
}
