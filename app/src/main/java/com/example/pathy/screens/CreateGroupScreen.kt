package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.design.pathyTopBarColors
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.CreateGroupViewModel
import com.example.pathy.data.Group
import com.example.pathy.data.openNavigationWithGroup
import com.example.pathy.ui.theme.PathyColors
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: (Group) -> Unit,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var validationMsg by remember { mutableStateOf<String?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var lastCreatedGroup by remember { mutableStateOf<Group?>(null) }
    var wasSyncedOnline by remember { mutableStateOf<Boolean?>(null) } // true=סונכרן, false=נשמר אופליין

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.group_create_title)) },
                colors = pathyTopBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            validationMsg = null
                        },
                        label = { Text(stringResource(R.string.group_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.group_description_label)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(if (isPublic) stringResource(R.string.group_public) else stringResource(R.string.group_private), fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                        }
                    }
                }
            }

            validationMsg?.let {
                Text(it, color = PathyColors.Error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        validationMsg = context.getString(R.string.group_name_required)
                        return@Button
                    }
                    isLoading = true

                    if (ActivityCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        validationMsg = context.getString(R.string.error_location_permission)
                        isLoading = false
                        return@Button
                    }

                    val fused = LocationServices.getFusedLocationProviderClient(context)
                    fused.lastLocation.addOnSuccessListener { location: Location? ->
                        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        val group = Group(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            description = description.trim(),
                            createdBy = userId,
                            lat = location?.latitude,
                            lng = location?.longitude,
                            isSynced = false,
                            isPublic = isPublic
                        )

                        viewModel.saveGroup(
                            group = group,
                            context = context,
                            onSaved = { online ->
                                isLoading = false
                                lastCreatedGroup = group.copy(isSynced = online)
                                wasSyncedOnline = online
                                showResultDialog = true
                            },
                            onError = {
                                isLoading = false
                                lastCreatedGroup = group
                                wasSyncedOnline = false
                                showResultDialog = true
                            }
                        )
                    }.addOnFailureListener {
                        validationMsg = context.getString(R.string.error_location_unavailable_retry)
                        isLoading = false
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoading) PathyColors.Secondary else PathyColors.Primary
                )
            ) {
                Text(if (isLoading) stringResource(R.string.common_saving) else stringResource(R.string.group_save_button), fontWeight = FontWeight.Bold)
            }
        }
    }

    GroupCreatedResultDialog(
        show = showResultDialog,
        wasSyncedOnline = wasSyncedOnline,
        onDismiss = { showResultDialog = false },
        onStart = {
            showResultDialog = false
            lastCreatedGroup?.let { g ->
                val user = FirebaseAuth.getInstance().currentUser
                if (wasSyncedOnline == true && user != null) {
                    viewModel.joinGroup(
                        groupId = g.id,
                        userId = user.uid,
                        userName = user.displayName ?: context.getString(R.string.default_user_name)
                    ) { /* לא חייבים לחכות */ }
                }
                openNavigationWithGroup(
                    context = context,
                    groupId = g.id,
                    fromGroups = true,
                    isLocalOnly = (wasSyncedOnline != true)
                )
                onGroupCreated(g)
            }
        }
    )
}

@Composable
private fun GroupCreatedResultDialog(
    show: Boolean,
    wasSyncedOnline: Boolean?,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    if (!show) return

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val title = when (wasSyncedOnline) {
                    true -> stringResource(R.string.group_created_synced)
                    false -> stringResource(R.string.group_saved_local)
                    else -> stringResource(R.string.group_saved)
                }

                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = PathyColors.Primary
                    )
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Accent),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_lets_go), fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close), color = PathyColors.Error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
