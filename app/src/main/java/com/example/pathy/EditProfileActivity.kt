package com.example.pathy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.pathy.i18n.PathyActivity
import com.example.pathy.data.ProfileDataStore
import com.example.pathy.data.ProfileInfo
import com.example.pathy.screens.EditProfileScreen
import kotlinx.coroutines.flow.first

class EditProfileActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ProfileDataStore(this)

        setContent {
            // The profile is read from disk off the main thread; the form appears as soon as it is ready.
            var profile by remember { mutableStateOf<ProfileInfo?>(null) }
            LaunchedEffect(Unit) { profile = store.profileFlow.first() }

            profile?.let {
                EditProfileScreen(
                    profile = it,
                    onSaveSuccess = { finish() }
                )
            }
        }
    }
}
