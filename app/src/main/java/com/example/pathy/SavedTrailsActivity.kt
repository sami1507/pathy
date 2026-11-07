package com.example.pathy

import android.os.Bundle
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.screens.SavedTrailsScreen
import com.example.pathy.ui.theme.setPathyContent

class SavedTrailsActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPathyContent {
            SavedTrailsScreen(onBack = { finish() })
        }
    }
}
