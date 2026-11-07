package com.example.pathy

import com.example.pathy.ui.theme.setPathyContent
import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.data.AuthViewModel
import com.example.pathy.data.CommunityViewModel
import com.example.pathy.data.ProfileDataStore
import com.example.pathy.data.ProfileInfo
import com.example.pathy.data.ProfileRepository.getProfileFromFirestore
import com.example.pathy.screens.ProfileScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ProfileActivity : PathyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        val viewedUserId = intent.getStringExtra("userId") ?: currentUser.uid
        val isCurrentUser = viewedUserId == currentUser.uid

        setPathyContent {
            val context = LocalContext.current
            val authViewModel: AuthViewModel = viewModel()
            val comViewModel: CommunityViewModel = viewModel()
            val store = remember { ProfileDataStore(context) }
            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = rememberUpdatedState(this)
            var profile by remember { mutableStateOf<ProfileInfo?>(null) }

            val editProfileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                coroutineScope.launch {
                    val updated = store.profileFlow.firstOrNull()
                    if (updated != null) profile = updated
                }
            }

            LaunchedEffect(viewedUserId) {
                if (isCurrentUser) {
                    val localProfile = store.profileFlow.firstOrNull()
                    if (localProfile != null) {
                        profile = localProfile
                    }
                } else {
                    try {
                        val fetchedProfile = getProfileFromFirestore(viewedUserId)
                        if (fetchedProfile != null) {
                            profile = fetchedProfile
                        } else {
                            Toast.makeText(context, context.getString(R.string.profile_not_found), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.profile_load_error, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isCurrentUser) {
                        val store = ProfileDataStore(context)
                        coroutineScope.launch {
                            val newProfile = store.profileFlow.firstOrNull()
                            if (newProfile != null) {
                                profile = newProfile
                            }
                        }
                    }
                }

                lifecycleOwner.value.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.value.lifecycle.removeObserver(observer)
                }
            }

            profile?.let {
                ProfileScreen(
                    profile = it,
                    isCurrentUser = isCurrentUser,
                    viewedUserId = viewedUserId,
                    onEditProfileClick = {
                        val intent = Intent(context, EditProfileActivity::class.java)
                        editProfileLauncher.launch(intent)
                    },
                    authViewModel = authViewModel,
                    comViewModel = comViewModel
                )
            }
        }
    }
}
