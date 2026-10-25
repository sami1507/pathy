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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            Surface(
                color = PathyColors.AppBar,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge.copy(color = Color.White))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.select_language), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
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

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable(
                    enabled = !locationPermissionGranted,
                    onClick = {
                        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (locationEnabled) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                        contentDescription = null,
                        tint = if (locationEnabled) PathyColors.Primary else PathyColors.Error,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_location_services),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (locationEnabled) stringResource(R.string.common_enabled) else stringResource(R.string.common_disabled),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

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
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PathyColors.Primary
                )
            ) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

@Composable
fun LanguageDropdown(
    selectedLanguage: String,
    languages: Map<String, String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, PathyColors.ProgressBar, RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
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
}

@Composable
fun LogoutButtonWithDialog(onLogoutConfirmed: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = PathyColors.Error
        )
    ) {
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

