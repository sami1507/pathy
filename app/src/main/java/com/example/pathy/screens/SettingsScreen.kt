package com.example.pathy.screens

import com.example.pathy.BuildConfig
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NatureTag
import com.example.pathy.ui.design.pathyTopBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pathy.RestartApp
import com.example.pathy.SignInActivity
import com.example.pathy.R
import com.example.pathy.i18n.AppLocale
import com.example.pathy.data.logout
import com.example.pathy.isLocationEnabled
import com.example.pathy.ui.theme.PathyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var locationEnabled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val selectedLanguage = remember { AppLocale.choice(context) }
    // Language names are shown in their own language, so they are never translated.
    val languages = linkedMapOf(
        AppLocale.SYSTEM to stringResource(R.string.language_system),
        AppLocale.ENGLISH to "English",
        AppLocale.ARABIC to "العربية",
        AppLocale.HEBREW to "עברית"
    )
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        locationEnabled = isGranted
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.settings_location_manual), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            locationPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            locationEnabled = isLocationEnabled(context)
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            NatureCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingTitle(icon = Icons.Filled.Language, title = stringResource(R.string.select_language))
                    LanguageDropdown(
                        selectedLanguage = selectedLanguage,
                        languages = languages,
                        onSelect = { code ->
                            coroutineScope.launch {
                                AppLocale.setChoice(context, code)
                                val intent = Intent(context, RestartApp::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            NatureCard(
                onClick = if (locationPermissionGranted) null else {
                    { permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingIcon(if (locationEnabled) Icons.Filled.LocationOn else Icons.Filled.LocationOff, error = !locationEnabled)
                    Text(
                        text = stringResource(R.string.settings_location_services),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    NatureTag(
                        label = if (locationEnabled) stringResource(R.string.common_enabled) else stringResource(R.string.common_disabled),
                        color = if (locationEnabled) PathyColors.Primary else PathyColors.Error
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            LogoutButtonWithDialog(
                onLogoutConfirmed = {
                    logout(context)
                    context.startActivity(Intent(context, SignInActivity::class.java))
                    (context as? Activity)?.finish()
                }
            )

            Text(
                text = stringResource(R.string.settings_version_label, BuildConfig.VERSION_NAME),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Round tinted icon at the start of a settings row. */
@Composable
private fun SettingIcon(icon: ImageVector, error: Boolean = false) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SettingTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingIcon(icon)
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun LanguageDropdown(
    selectedLanguage: String,
    languages: Map<String, String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = languages[selectedLanguage] ?: "",
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            languages.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LogoutButtonWithDialog(onLogoutConfirmed: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.sign_out), style = MaterialTheme.typography.titleMedium)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onLogoutConfirmed()
                }) {
                    Text(stringResource(R.string.sign_out), color = PathyColors.Secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = PathyColors.Primary)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = PathyColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_out_confirm_title), style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Text(stringResource(R.string.sign_out_confirm_message), style = MaterialTheme.typography.bodyLarge)
            }
        )
    }
}

