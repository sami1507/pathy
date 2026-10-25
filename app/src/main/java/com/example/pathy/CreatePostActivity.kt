package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.pathy.data.SavedTrail
import com.example.pathy.screens.CreatePostScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import com.google.gson.Gson

class CreatePostActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TEMPATHYTheme {
                var isTrailLoading by remember { mutableStateOf(true) }
                val trailIdFromIntent = intent.getStringExtra("trailId")
                val trailJsonFromIntent = intent.getStringExtra("selectedTrailJson")
                var selectedTrail by remember { mutableStateOf<SavedTrail?>(null) }

                LaunchedEffect(trailIdFromIntent, trailJsonFromIntent) {
                    if (!trailJsonFromIntent.isNullOrBlank()) {
                        selectedTrail = Gson().fromJson(trailJsonFromIntent, SavedTrail::class.java)
                        isTrailLoading = false
                        return@LaunchedEffect
                    }

                    if (!trailIdFromIntent.isNullOrBlank()) {
                        val trail = fetchTrailFromFirestore(trailIdFromIntent)
                        selectedTrail = trail ?: SavedTrail()
                    }

                    isTrailLoading = false
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val returnedJson = result.data?.getStringExtra("selectedTrailJson")
                        if (!returnedJson.isNullOrBlank()) {
                            selectedTrail = Gson().fromJson(returnedJson, SavedTrail::class.java)
                        }
                    }
                }

                if (isTrailLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    CreatePostScreen(
                        selectedTrail = selectedTrail,
                        onSelectTrailClick = {
                            val intent = Intent(this, CloudSavedTrailsActivity::class.java).apply {
                                putExtra("isSelectionMode", true)
                                putExtra("source", "create_post")
                            }
                            launcher.launch(intent)
                        }
                    )
                }
            }
        }
    }
}