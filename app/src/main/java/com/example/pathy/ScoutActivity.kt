package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.screens.ScoutChatScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

/** Scout AI chat: opened from the Scout button on the map and from the home screen. */
class ScoutActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                ScoutChatScreen(
                    onBack = { finish() },
                    onNavigateToTrails = {
                        startActivity(Intent(this, TrailDiscoveryActivity::class.java))
                    },
                    onNavigateToMap = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }
}
