package com.example.pathy

import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.screens.SettingsScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

class SettingsActivity : PathyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TEMPATHYTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}
