package com.example.pathy

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.PathyActivity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import com.example.pathy.screens.WalkieTalkieScreen

@RequiresApi(Build.VERSION_CODES.O)
class WalkieTalkieActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId  = intent.getStringExtra("extra_group_id").orEmpty()
        val isHost   = intent.getBooleanExtra("extra_is_host", true)
        val userName = intent.getStringExtra("extra_user_name").orEmpty()

        setPathyContent {
            WalkieTalkieScreen(
                presetGroupId = groupId.ifBlank { null },
                presetIsHost = isHost,
                presetUserName = userName.ifBlank { null }
            )
        }
    }
}