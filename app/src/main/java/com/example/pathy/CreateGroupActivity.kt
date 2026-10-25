package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.pathy.screens.CreateGroupScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

class CreateGroupActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                CreateGroupScreen(
                    onGroupCreated = {finish()}
                )
            }
        }
    }
}
