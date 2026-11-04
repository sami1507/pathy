package com.example.pathy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.history.ActivityDetails
import com.example.pathy.history.ActivityHistoryManager
import com.example.pathy.history.LocalActivities
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.screens.ActivityDetailsPlaceholder
import com.example.pathy.screens.ActivityDetailsScreen
import com.example.pathy.screens.ActivityHistoryScreen
import com.example.pathy.tracking.RecordingRepository
import com.example.pathy.ui.theme.TEMPATHYTheme

/**
 * The signed-in user's recorded outings: the list (cloud history plus anything still waiting on the
 * phone) and, on tap, one outing with its route.
 */
class ActivityHistoryActivity : PathyActivity() {

    private val history = ActivityHistoryManager()
    private val local by lazy { RecordingRepository(DatabaseProvider.getDatabase(applicationContext).recordingDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                var selected by remember { mutableStateOf<String?>(null) }
                var details by remember { mutableStateOf<ActivityDetails?>(null) }
                var failed by remember { mutableStateOf(false) }
                var retry by remember { mutableStateOf(0) }
                var listVersion by remember { mutableStateOf(0) }

                LaunchedEffect(selected, retry) {
                    details = null
                    failed = false
                    val id = selected ?: return@LaunchedEffect
                    details = loadDetails(id)
                    failed = details == null
                }

                val open = selected
                Box(Modifier.fillMaxSize()) {
                    // The list stays composed under the detail view, so the filter and the scroll position
                    // are still there when the user comes back. Screen readers skip it while it is covered.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (open != null) Modifier.clearAndSetSemantics { } else Modifier)
                    ) {
                        ActivityHistoryScreen(
                            onBack = { finish() },
                            onActivityClick = { selected = it },
                            onRecord = { startActivity(Intent(this@ActivityHistoryActivity, TrackingActivity::class.java)) },
                            refreshKey = listVersion
                        )
                    }
                    if (open != null) {
                        BackHandler { selected = null }
                        val current = details
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                // Taps on the covered list must not get through.
                                .pointerInput(Unit) { detectTapGestures { } }
                        ) {
                            if (current != null) {
                                ActivityDetailsScreen(
                                    details = current,
                                    onBack = { selected = null },
                                    onDelete = {
                                        val ok = delete(open)
                                        if (ok) {
                                            Toast.makeText(this@ActivityHistoryActivity, getString(R.string.activity_deleted), Toast.LENGTH_SHORT).show()
                                            listVersion++
                                            selected = null
                                        }
                                        ok
                                    }
                                )
                            } else {
                                ActivityDetailsPlaceholder(failed = failed, onRetry = { retry++ }, onBack = { selected = null })
                            }
                        }
                    }
                }
            }
        }
    }

    /** The phone's own copy first (instant, works offline); the cloud history otherwise. */
    private suspend fun loadDetails(id: String): ActivityDetails? {
        local.load(id)?.let { return LocalActivities.details(it) }
        return history.getActivityDetails(id).getOrNull()
    }

    private suspend fun delete(id: String): Boolean {
        val pendingOnly = local.load(id)?.recording?.uploaded == false
        // An outing that never reached the cloud has nothing to delete there.
        val cloudOk = pendingOnly || history.deleteActivity(id).isSuccess
        if (cloudOk) local.discard(id)
        return cloudOk
    }
}
