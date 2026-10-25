package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import com.example.pathy.data.TrailData
import com.example.pathy.screens.TrailDetailsScreen
import com.google.gson.Gson

class TrailDetailsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trailJson = intent.getStringExtra("trail")
        val trail: TrailData? = runCatching { Gson().fromJson(trailJson, TrailData::class.java) }.getOrNull()

        if (trail == null) {
            Log.e("TrailDetailsActivity", "Trail data missing or malformed – finishing activity")
            Toast.makeText(this, this.getString(R.string.trail_data_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                TrailDetailsScreen(trail = trail)
            }
        }
    }

    companion object {
        fun createIntent(context: Context, trail: TrailData): Intent {
            return Intent(context, TrailDetailsActivity::class.java).apply {
                putExtra("trail", Gson().toJson(trail))
            }
        }
    }
}
