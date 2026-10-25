package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.screens.AttendeesScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

class AttendeesActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra("eventId") ?: return

        setContent {
            TEMPATHYTheme {
                AttendeesScreen(eventId)
            }
        }
    }
}
