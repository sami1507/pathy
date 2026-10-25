package com.example.pathy

import androidx.compose.ui.res.stringResource
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.ui.theme.PathyColors

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import com.example.pathy.data.SavedTrail
import com.google.gson.Gson
import com.example.pathy.screens.OfflineTrailDetailsScreen
import java.io.File

class OfflineTrailDetailsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra("offlineTrailPath")
        Log.d("OfflineTrail", "Received file path: $filePath")

        val savedTrail = filePath?.let { path ->
            val file = File(path)
            Log.d("OfflineTrail", "File exists: ${file.exists()}")

            if (file.exists()) {
                try {
                    val json = file.readText()
                    Log.d("OfflineTrail", "File content: $json")

                    val trail = Gson().fromJson(json, SavedTrail::class.java)
                    Log.d("OfflineTrail", "Parsed trail name: ${trail.name}")
                    trail
                } catch (e: Exception) {
                    Log.e("OfflineTrail", "Error parsing SavedTrail JSON", e)
                    null
                }
            } else {
                Log.e("OfflineTrail", "File does not exist at path: $path")
                null
            }
        }

        setContent {
            MaterialTheme {
                if (savedTrail != null) {
                    OfflineTrailDetailsScreen(savedTrail)
                } else {
                    Text(stringResource(R.string.trail_load_failed), color = PathyColors.Error)
                }
            }
        }
    }
}



