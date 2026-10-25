package com.example.pathy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.screens.TrackingScreen
import com.example.pathy.tracking.RecordingController
import com.example.pathy.tracking.RecordingHub
import com.example.pathy.tracking.RecordingService
import com.google.firebase.auth.FirebaseAuth

/**
 * Starts, pauses and finishes an outing recording. The recording itself runs in [RecordingService]
 * (it keeps going with the screen off or this screen closed); this screen only shows its state and
 * sends commands. A finished outing is saved on the phone first and uploaded to the history when
 * there is a connection (see UploadWorker).
 */
class TrackingActivity : PathyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Reopening the screen after the app was killed during an outing: bring the outing back.
        if (!RecordingService.isRunning && hasLocationPermission()) RecordingController.restore(this)

        setContent {
            val context = LocalContext.current
            val state by RecordingHub.state.collectAsState()
            val stats by RecordingHub.stats.collectAsState()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { granted ->
                if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                    RecordingController.start(context)
                } else {
                    Toast.makeText(context, context.getString(R.string.tracking_location_permission_needed), Toast.LENGTH_LONG).show()
                }
            }

            LaunchedEffect(Unit) {
                RecordingHub.finished.collect { onRecordingFinished() }
            }

            TrackingScreen(
                recordingState = state,
                stats = stats,
                onStart = {
                    if (hasLocationPermission()) RecordingController.start(context)
                    else permissionLauncher.launch(requiredPermissions())
                },
                onPause = { RecordingController.pause(context) },
                onResume = { RecordingController.resume(context) },
                onFinish = { RecordingController.stop(context) },
                onClose = { finish() }
            )
        }
    }

    private fun onRecordingFinished() {
        val signedIn = FirebaseAuth.getInstance().currentUser != null
        val message = if (signedIn) R.string.rec_saved_on_phone else R.string.rec_sign_in_hint
        Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    /** Location is required; the notification permission only decides whether the status is visible. */
    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}
