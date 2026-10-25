package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.screens.FindNearbyGroupsScreen

class FindNearbyGroupsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FindNearbyGroupsScreen(
                radiusMeters = 5_000f,
                onBack = { finish() }
            )
        }
    }
}
