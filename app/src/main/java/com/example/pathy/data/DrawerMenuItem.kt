package com.example.pathy.data

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pathy.CommunityActivity
import com.example.pathy.ProfileActivity
import com.example.pathy.R
import com.example.pathy.SavedTrailsActivity
import com.example.pathy.SearchActivity
import com.example.pathy.SessionState
import com.example.pathy.SettingsActivity
import com.example.pathy.SignInActivity
import com.example.pathy.ui.theme.PathyColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class DrawerMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * The side menu of the home map. Every entry has an icon and a name: icons alone made people
 * guess, and a screen reader had nothing to announce for the sheet as a whole.
 */
@Composable
fun FancyDrawerMenu(
    drawerState: DrawerState,
    scope: CoroutineScope,
    context: Context,
    onOpenMapStyleDialog: () -> Unit
) {
    fun close() {
        scope.launch { drawerState.close() }
    }

    val menuItems = listOf(
        DrawerMenuItem(stringResource(R.string.common_search), Icons.Filled.Search) {
            close()
            context.startActivity(Intent(context, SearchActivity::class.java))
        },
        // Saved trails need no account and no connection, so they are one tap from the map.
        DrawerMenuItem(stringResource(R.string.saved_trails_title), Icons.Filled.CloudDone) {
            close()
            context.startActivity(Intent(context, SavedTrailsActivity::class.java))
        },
        DrawerMenuItem(stringResource(R.string.community_title), Icons.Filled.Forum) {
            close()
            context.startActivity(Intent(context, CommunityActivity::class.java))
        },
        DrawerMenuItem(stringResource(R.string.home_profile), Icons.Filled.AccountCircle) {
            close()
            (context as? ComponentActivity)?.lifecycleScope?.launch {
                val isValid = performLoadingChecks(context)
                SessionState.hasLaunched = true
                val target = if (isValid) ProfileActivity::class.java else SignInActivity::class.java
                context.startActivity(Intent(context, target))
            }
        },
        DrawerMenuItem(stringResource(R.string.menu_map), Icons.Filled.Public) {
            close()
            onOpenMapStyleDialog()
        },
        DrawerMenuItem(stringResource(R.string.settings), Icons.Filled.Settings) {
            close()
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    )

    DrawerSheet(menuItems)
}

/** The drawer itself, without any navigation, so it can be rendered and tested on its own. */
@Composable
internal fun DrawerSheet(items: List<DrawerMenuItem>) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        // The sheet already starts below the top bar, so it must not add the status bar again.
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.width(300.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DrawerHeader()
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label, style = MaterialTheme.typography.titleMedium) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = false,
                        onClick = item.onClick,
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PathyColors.AppBar)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Terrain, contentDescription = null, tint = PathyColors.Primary)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
    }
}
