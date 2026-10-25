package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.pathy.data.AuthViewModel
import kotlinx.coroutines.launch
import com.example.pathy.ui.screens.CommunityScreen
import com.example.pathy.ui.theme.TEMPATHYTheme

class CommunityActivity : PathyActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back returns to the map (MainActivity) instead of just closing this screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@CommunityActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
        })

        val fromPost = intent?.getBooleanExtra("fromPost", false) ?: false
        val postText = intent?.getStringExtra("postText")
        val trailId = intent?.getStringExtra("trailId")
        val trailName = intent?.getStringExtra("trailName")
        val trailRating = intent.getFloatExtra("trailRating", 0f)
        val postImageUris  = intent?.getStringArrayListExtra("postImageUris")?.toList() ?: emptyList()
        val fromEvent = intent.getBooleanExtra("fromEvent", false)
        val eventTitle = intent?.getStringExtra("eventTitle")
        val eventDescription = intent?.getStringExtra("eventDescription")
        val eventStartDate = intent?.getStringExtra("eventStartDate")
        val eventEndDate = intent?.getStringExtra("eventEndDate")
        val eventStartTime = intent?.getStringExtra("eventStartTime")
        val eventEndTime = intent?.getStringExtra("eventEndTime")
        val eventLocation = intent?.getStringExtra("eventLocation")
        val imageUris = intent?.getStringArrayListExtra("eventImageUris")
        val isRegistrationOpen = intent.getBooleanExtra("isRegistrationOpen", true)
        Log.d("EVENT_INTENT", "fromEvent=$fromEvent, eventTitle=$eventTitle, trailId=$trailId")

        setContent {
            TEMPATHYTheme {
                CommunityScreen(
                    fromPost = fromPost,
                    postText = postText,
                    trailId = trailId,
                    trailName = trailName,
                    trailRating = trailRating,
                    postImageUris = postImageUris,
                    fromEvent = fromEvent,
                    eventTitle = eventTitle,
                    eventDescription = eventDescription,
                    eventStartDate = eventStartDate,
                    eventEndDate = eventEndDate,
                    eventStartTime = eventStartTime,
                    eventEndTime = eventEndTime,
                    eventLocation = eventLocation,
                    eventImageUris = imageUris,
                    isEventRegistrationOpen = isRegistrationOpen,
                    onCreatePostClick = {
                        lifecycleScope.launch {
                            authViewModel.checkUserAndConnection(
                                context = this@CommunityActivity,
                                onRedirectToLogin = {
                                    startActivity(Intent(this@CommunityActivity, SignInActivity::class.java))
                                },
                                onContinueWithWarning = {
                                    val intent = Intent(this@CommunityActivity, CreatePostActivity::class.java)
                                    intent.putExtra("showConnectionDialog", true)
                                    startActivity(intent)
                                },
                                onAllowed = {
                                    startActivity(Intent(this@CommunityActivity, CreatePostActivity::class.java))
                                }
                            )
                        }
                    },
                    onCreateEventClick = {
                        lifecycleScope.launch {
                            authViewModel.checkUserAndConnection(
                                context = this@CommunityActivity,
                                onRedirectToLogin = {
                                    startActivity(Intent(this@CommunityActivity, SignInActivity::class.java))
                                },
                                onContinueWithWarning = {
                                    val intent = Intent(this@CommunityActivity, CreateEventActivity::class.java)
                                    intent.putExtra("showConnectionDialog", true)
                                    startActivity(intent)
                                },
                                onAllowed = {
                                    startActivity(Intent(this@CommunityActivity, CreateEventActivity::class.java))
                                }
                            )
                        }
                    }
                )
            }
        }
    }

}