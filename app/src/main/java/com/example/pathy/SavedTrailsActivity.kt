package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.data.SavedTrail
import com.example.pathy.ui.theme.TEMPATHYTheme
import com.example.pathy.screens.SavedTrailsScreen
import com.google.gson.Gson

class SavedTrailsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                SavedTrailsScreen()
            }
        }
    }
}
