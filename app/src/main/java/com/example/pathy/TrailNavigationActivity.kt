package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.TrailData
import com.example.pathy.data.from
import com.example.pathy.screens.TrailNavigationScreen
import com.google.gson.Gson

class TrailNavigationActivity : PathyActivity() {

    // Not exported: only StartTrailButton opens this, and it checks the location permissions first.
    // The explicit check below covers the rest of the flow.
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasLocation) {
            Toast.makeText(this, this.getString(R.string.nav_location_permission_needed), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)

        // Senders put the full TrailData JSON (TrailDetailsScreen / ProfileScreen), so parse that
        // type and convert. Parsing as SavedTrail let a null midpoint into a non-null field.
        val trail: SavedTrail? = intent.getStringExtra("trail")
            ?.let { json -> runCatching { Gson().fromJson(json, TrailData::class.java) }.getOrNull() }
            ?.let { data -> from(data, createdByUid = "") }

        // A trail needs at least two points to have a direction to follow.
        if (trail == null || trail.geoPoints.size < 2) {
            Toast.makeText(this, this.getString(R.string.trail_data_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            TrailNavigationScreen(trail = trail, userLat = lat, userLng = lng)
        }
    }
}
