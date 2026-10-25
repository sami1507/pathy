package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.pathy.history.ActivityHistoryManager
import com.example.pathy.history.PeriodStatistics
import com.example.pathy.screens.OnboardingScreen
import com.example.pathy.screens.UltraModernHomeScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import com.google.firebase.auth.FirebaseAuth

private const val PREFS_APP = "app_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_done"

/**
 * App entry point. Shows onboarding once, then the home screen. Every home action opens a
 * feature screen; the map is one tap away.
 */
class HomeActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                HomeRoute()
            }
        }
    }
}

private fun openScreen(context: Context, target: Class<*>) {
    context.startActivity(Intent(context, target))
}

@Composable
private fun HomeRoute() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE) }
    var onboardingDone by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false)) }

    if (!onboardingDone) {
        OnboardingScreen(onComplete = {
            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
            onboardingDone = true
        })
        return
    }

    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var refresh by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(auth, lifecycle) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        auth.addAuthStateListener(listener)
        lifecycle.addObserver(observer)
        onDispose { auth.removeAuthStateListener(listener); lifecycle.removeObserver(observer) }
    }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<PeriodStatistics?>(null) }

    LaunchedEffect(user?.uid, refresh) {
        stats = null
        failed = false
        loading = false
        val uid = user?.uid ?: return@LaunchedEffect
        loading = true
        ActivityHistoryManager()
            .getStatistics(uid, startDate = 0L, endDate = System.currentTimeMillis())
            .onSuccess { stats = it }
            .onFailure { failed = true }
        loading = false
    }

    val totalActivities = stats?.activityCount ?: 0
    UltraModernHomeScreen(
        userName = user?.displayName?.takeIf { it.isNotBlank() },
        totalDistance = stats?.totalDistance ?: 0f,
        totalActivities = totalActivities,
        // Level is derived from recorded activities: one level per five activities
        currentLevel = 1 + totalActivities / 5,
        onStartTracking = { openScreen(context, TrackingActivity::class.java) },
        onDiscoverTrails = { openScreen(context, TrailDiscoveryActivity::class.java) },
        onViewProfile = { openScreen(context, ProfileActivity::class.java) },
        onOpenScout = { openScreen(context, ScoutActivity::class.java) },
        onViewHistory = { openScreen(context, ActivityHistoryActivity::class.java) },
        onOpenMap = { openScreen(context, MainActivity::class.java) },
        statsLoading = loading, statsError = failed, signedIn = user != null,
        onRetry = { refresh++ },
        weatherContent = { com.example.pathy.components.HomeWeatherCard() }
    )
}
