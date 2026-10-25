package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.ui.screens.EventDetailsScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

class EventDetailsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getStringExtra("eventId")
        setContent {
            TEMPATHYTheme {
                EventDetailsScreen(eventId)
            }
        }
    }
}