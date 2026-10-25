package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.needVoiceRuntimePerms
import com.example.pathy.requestVoicePermissions
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.walkietalkie.TransportCoordinator
import com.example.pathy.walkietalkie.WalkieTalkieService
import kotlinx.coroutines.launch

private enum class PendingAction { NONE, START_COMM, TOGGLE_MIC }

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("ImplicitSamInstance")
@Composable
fun WalkieTalkieScreen(
    presetGroupId: String? = null,
    presetIsHost: Boolean? = null,
    presetUserName: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    var userName by rememberSaveable { mutableStateOf(presetUserName ?: context.getString(R.string.default_user_numbered, (100..999).random().toString())) }
    var groupId by rememberSaveable { mutableStateOf(presetGroupId ?: "") }
    var isHost by rememberSaveable { mutableStateOf(presetIsHost ?: true) }
    var isCommunicating by rememberSaveable { mutableStateOf(false) }
    var currentSpeaker by rememberSaveable { mutableStateOf<String?>(null) }
    var microphoneEnabled by rememberSaveable { mutableStateOf(false) }
    var pendingAction by rememberSaveable { mutableStateOf(PendingAction.NONE) }
    var connectionModeText by rememberSaveable { mutableStateOf(context.getString(R.string.walkie_connecting)) }
    val peers by TransportCoordinator.peersFlow.collectAsState()
    val displayUsers = remember(peers, userName, isCommunicating) {
        val base = if (isCommunicating && userName.isNotBlank() && userName !in peers)
            listOf(userName) + peers
        else
            peers
        base.distinct()
    }

    LaunchedEffect(Unit) {
        isCommunicating = TransportCoordinator.isActive
        microphoneEnabled = TransportCoordinator.isMicEnabled
        currentSpeaker = TransportCoordinator.getCurrentSpeaker()
    }
    
    // Update connection status periodically
    LaunchedEffect(isCommunicating) {
        while (isCommunicating) {
            val status = TransportCoordinator.getConnectionStatus()
            connectionModeText = when (status?.type) {
                com.example.pathy.walkietalkie.ConnectionType.INTERNET -> context.getString(R.string.walkie_mode_internet)
                com.example.pathy.walkietalkie.ConnectionType.LOCAL_P2P -> context.getString(R.string.walkie_mode_local, status.estimatedRangeMeters.toString())
                else -> context.getString(R.string.walkie_mode_connecting)
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    val requestPermsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        when {
            allGranted && pendingAction == PendingAction.START_COMM -> {
                pendingAction = PendingAction.NONE
                TransportCoordinator.start(
                    context = context,
                    userName = userName,
                    groupId = groupId.trim(),
                    isHost = isHost,
                    onSpeakerChanged = { name -> scope.launch { currentSpeaker = name } },
                    onError = { t ->
                        scope.launch {
                            isCommunicating = false
                            currentSpeaker = null
                            microphoneEnabled = false
                            snackBarHostState.showSnackbar(t.message ?: context.getString(R.string.error_generic))
                        }
                    }
                )
                isCommunicating = true
                microphoneEnabled = TransportCoordinator.isMicEnabled
            }
            allGranted && pendingAction == PendingAction.TOGGLE_MIC -> {
                pendingAction = PendingAction.NONE
                TransportCoordinator.setMicEnabled(!TransportCoordinator.isMicEnabled)
                microphoneEnabled = TransportCoordinator.isMicEnabled
            }
            else -> {
                pendingAction = PendingAction.NONE
                scope.launch { snackBarHostState.showSnackbar(context.getString(R.string.walkie_permissions_needed)) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackBarHostState) },
            topBar = {
                Surface(
                    color = PathyColors.AppBar,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.walkie_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 3.dp
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = { Text(stringResource(R.string.walkie_username_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCommunicating
                        )
                        if (presetGroupId.isNullOrBlank()) {
                            OutlinedTextField(
                                value = groupId,
                                onValueChange = { groupId = it },
                                label = { Text(stringResource(R.string.walkie_room_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isCommunicating
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(stringResource(R.string.walkie_mode_label), style = MaterialTheme.typography.bodyMedium)
                            RadioButton(selected = isHost, onClick = { if (!isCommunicating) isHost = true })
                            Text(stringResource(R.string.walkie_host))
                            RadioButton(selected = !isHost, onClick = { if (!isCommunicating) isHost = false })
                            Text(stringResource(R.string.walkie_join))
                        }
                    }
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (groupId.trim().isEmpty()) {
                                    scope.launch { snackBarHostState.showSnackbar(context.getString(R.string.walkie_enter_code)) }
                                    return@Button
                                }
                                if (needVoiceRuntimePerms(context)) {
                                    pendingAction = PendingAction.START_COMM
                                    requestVoicePermissions(requestPermsLauncher)
                                } else {
                                    TransportCoordinator.start(
                                        context = context,
                                        userName = userName,
                                        groupId = groupId.trim(),
                                        isHost = isHost,
                                        onSpeakerChanged = { name -> scope.launch { currentSpeaker = name } },
                                        onError = {}
                                    )
                                    isCommunicating = true
                                    microphoneEnabled = TransportCoordinator.isMicEnabled
                                }
                            },
                            enabled = !isCommunicating,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
                        ) { Text(stringResource(R.string.walkie_start), color = Color.White) }

                        OutlinedButton(
                            onClick = {
                                TransportCoordinator.stop()
                                isCommunicating = false
                                microphoneEnabled = false
                                currentSpeaker = null
                            },
                            enabled = isCommunicating,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PathyColors.Primary)
                        ) { Text(stringResource(R.string.walkie_stop)) }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.walkie_current_speaker, currentSpeaker ?: stringResource(R.string.walkie_no_speaker)),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = PathyColors.Title
                    )
                    
                    if (isCommunicating) {
                        Surface(
                            color = when {
                                connectionModeText.contains("Internet") -> PathyColors.Primary
                                connectionModeText.contains("Local") -> PathyColors.Warning
                                else -> Color.Gray
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = connectionModeText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                        }
                    }
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 3.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.walkie_participants, displayUsers.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PathyColors.Title
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn {
                            items(displayUsers) { userId ->
                                val isSpeaking = userId == currentSpeaker
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    if (isSpeaking) Text("🎙️ ", modifier = Modifier.padding(end = 4.dp))
                                    Text(userId, style = MaterialTheme.typography.bodyMedium, color = PathyColors.Title)
                                }
                                Divider(color = PathyColors.CardBorder, thickness = 0.8.dp)
                            }
                        }
                    }
                }

                // כפתור מיקרופון – עגול ובולט בתחתית
                if (isCommunicating) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FloatingActionButton(
                            onClick = {
                                if (needVoiceRuntimePerms(context)) {
                                    pendingAction = PendingAction.TOGGLE_MIC
                                    requestVoicePermissions(requestPermsLauncher)
                                } else {
                                    TransportCoordinator.setMicEnabled(!TransportCoordinator.isMicEnabled)
                                    microphoneEnabled = TransportCoordinator.isMicEnabled
                                }
                            },
                            containerColor = if (microphoneEnabled) PathyColors.Secondary else Color.DarkGray,
                            contentColor = Color.White,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.nav_mic_desc))
                        }
                    }
                }
            }
        }
    }
}