package com.example.pathy.data

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.content.Context
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.mapbox.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pathy.SettingsActivity
import com.example.pathy.SearchActivity
import com.example.pathy.ProfileActivity
import com.example.pathy.CommunityActivity
import com.example.pathy.SignInActivity
import com.example.pathy.ui.theme.PathyColors
import kotlinx.coroutines.delay
import com.example.pathy.SessionState


data class DrawerMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun FancyDrawerMenu(
    drawerState: DrawerState,
    scope: CoroutineScope,
    context: Context,
    lastLocation: Point?,
    onOpenMapStyleDialog: () -> Unit
) {
    val iconSize = 36.dp
    val buttonSize = 72.dp
    val spacing = 24.dp

    val itemColors = listOf(
        PathyColors.MenuSearchColor,
        PathyColors.MenuCommunityColor,
        PathyColors.MenuProfileColor,
        PathyColors.MenuMapColor,
        PathyColors.MenuSettingsColor
    )

    val menuItems = listOf(
        DrawerMenuItem(stringResource(R.string.common_search), Icons.Filled.Search) {
            scope.launch { drawerState.close() }
            lastLocation?.let {
                Intent(context, SearchActivity::class.java).apply {
                }.also(context::startActivity)
            }
        },
        DrawerMenuItem(stringResource(R.string.community_title), Icons.Filled.Forum) {
            scope.launch { drawerState.close() }
            context.startActivity(Intent(context, CommunityActivity::class.java))
        },
        DrawerMenuItem(stringResource(R.string.home_profile), Icons.Filled.AccountCircle) {
            scope.launch {
                drawerState.close()
                val activity = context as? ComponentActivity
                activity?.lifecycleScope?.launch {
                    val isValid = performLoadingChecks(context)
                    val intent = if (isValid) {
                        SessionState.hasLaunched = true
                        Intent(context, ProfileActivity::class.java)
                    } else {
                        SessionState.hasLaunched = true
                        Intent(context, SignInActivity::class.java)
                    }
                    context.startActivity(intent)
                }
            }
        },
        DrawerMenuItem(stringResource(R.string.menu_map), Icons.Filled.Public, onOpenMapStyleDialog),
        DrawerMenuItem(stringResource(R.string.settings), Icons.Filled.Settings) {
            scope.launch { drawerState.close() }
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    )

    ModalDrawerSheet(
        drawerContainerColor = PathyColors.AppBar,
        drawerContentColor = Color.White,
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            menuItems.forEachIndexed { index, item ->
                val color = itemColors.getOrNull(index) ?: Color.White

                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(
                            color = itemColors.getOrElse(index) { Color.White.copy(alpha = 0.15f) },
                            shape = CircleShape
                        )
                        .clickable { item.onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = PathyColors.Background,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

