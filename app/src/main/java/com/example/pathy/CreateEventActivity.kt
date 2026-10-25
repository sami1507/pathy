package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.pathy.data.SavedTrail
import com.example.pathy.screens.CreateEventScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import com.google.gson.Gson

class CreateEventActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TEMPATHYTheme {
                var selectedTrailJson by rememberSaveable { mutableStateOf<String?>(null) }

                val trailIdFromIntent = intent.getStringExtra("trailId")
                LaunchedEffect(trailIdFromIntent) {
                    if (trailIdFromIntent != null && selectedTrailJson == null) {
                        val trail = fetchTrailFromFirestore(trailIdFromIntent)
                        if (trail != null) {
                            selectedTrailJson = Gson().toJson(trail)
                        }
                    }
                }

                val selectedTrail = selectedTrailJson?.let {
                    runCatching { Gson().fromJson(it, SavedTrail::class.java) }.getOrNull()
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val returnedJson = result.data?.getStringExtra("selectedTrailJson")
                        if (!returnedJson.isNullOrBlank()) {
                            selectedTrailJson = returnedJson
                        }
                    }
                }

                CreateEventScreen(
                    selectedTrail = selectedTrail,
                    onSelectTrailClick = {
                        val intent = Intent(this, CloudSavedTrailsActivity::class.java).apply {
                            putExtra("isSelectionMode", true)
                            putExtra("source", "create_event")
                        }
                        launcher.launch(intent)
                    }
                )
            }
        }
    }
}
