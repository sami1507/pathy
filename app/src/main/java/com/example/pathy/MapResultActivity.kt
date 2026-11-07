package com.example.pathy

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.data.TrailData
import com.example.pathy.screens.MapResultScreen
import com.mapbox.geojson.Point

class MapResultActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)
        val trailsJson = intent.getStringExtra("trailsJson") ?: "[]"
        val trails = TrailData.fromJson(trailsJson)
        val selectedPoint = Point.fromLngLat(lng, lat)

        setPathyContent {
            MapResultScreen(centerPoint = selectedPoint, trails = trails, onBack = { finish() })
        }
    }
}

