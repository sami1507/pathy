package com.example.pathy.ui.screens

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.PathyActivity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import com.example.pathy.screens.NavigationScreen

@RequiresApi(Build.VERSION_CODES.O)
class NavigationActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)
        val groupId     = intent.getStringExtra("groupId")
        val fromGroups  = intent.getBooleanExtra("fromGroups", false)

        setPathyContent {
            NavigationScreen(
                lat = lat,
                lng = lng,
                groupId = groupId,
                fromGroups = fromGroups
            )
        }
    }
}
