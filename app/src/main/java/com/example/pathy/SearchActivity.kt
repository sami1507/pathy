package com.example.pathy

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.screens.SearchScreen

class SearchActivity : PathyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPathyContent {
            SearchScreen()
        }
    }
}
