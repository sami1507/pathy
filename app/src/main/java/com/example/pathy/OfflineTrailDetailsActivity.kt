package com.example.pathy

import android.os.Bundle
import android.util.Log
import com.example.pathy.data.TrailData
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.screens.TrailDetailsScreen
import com.example.pathy.ui.theme.setPathyContent
import com.google.gson.Gson
import java.io.File

/**
 * Opens a trail that was saved on the phone. It is the same screen as any trail: it notices by itself
 * that the trail is saved and that there is no connection. Kept as its own entry so links to saved
 * trails (a file path) keep working.
 */
class OfflineTrailDetailsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trail: TrailData? = intent.getStringExtra("offlineTrailPath")?.let { path ->
            runCatching { Gson().fromJson(File(path).readText(), TrailData::class.java) }
                .onFailure { Log.w("OfflineTrail", "Could not read the saved trail", it) }
                .getOrNull()
        }?.takeIf { it.geoPoints.size >= 2 }

        if (trail == null) {
            android.widget.Toast.makeText(this, getString(R.string.trail_load_failed), android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setPathyContent {
            TrailDetailsScreen(trail = trail)
        }
    }
}
