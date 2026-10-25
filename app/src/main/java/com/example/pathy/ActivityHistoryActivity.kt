package com.example.pathy

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.history.ActivityDetails
import com.example.pathy.history.ActivityHistoryManager
import com.example.pathy.history.LocalActivities
import com.example.pathy.i18n.PathyActivity
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
                if (open == null) {
                    ActivityHistoryScreen(
                        onBack = { finish() },
                        onActivityClick = { selected = it },
                        refreshKey = listVersion
                    )
                } else {
                    BackHandler { selected = null }
                    val current = details
                    when {
                        current != null -> ActivityDetailsScreen(
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
                        failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.load_failed))
                                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.back)) }
                            }
                        }
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
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
