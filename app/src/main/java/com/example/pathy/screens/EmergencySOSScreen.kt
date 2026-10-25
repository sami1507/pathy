package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.theme.PathyColors

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathy.emergency.EmergencyContact

/**
 * 🚨 EMERGENCY SOS SCREEN
 * 
 * Life-saving emergency features
 * - One-tap SOS
 * - Emergency contacts
 * - Live location sharing
 * - Critical safety feature!
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySOSScreen(
    emergencyContacts: List<EmergencyContact>,
    onTriggerSOS: () -> Unit = {},
    onCallEmergency: () -> Unit = {},
    onShareLocation: () -> Unit = {},
    onAddContact: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var showSOSConfirmation by remember { mutableStateOf(false) }
    
    // Pulsing animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sos_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main SOS Button
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(PathyColors.Error, PathyColors.Accent)
                                )
                            )
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier
                                    .size(120.dp)
                                    .scale(pulse)
                                    .clickable(onClick = { showSOSConfirmation = true })
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "SOS",
                                        tint = PathyColors.Error,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = stringResource(R.string.sos_hero_title),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Text(
                                text = stringResource(R.string.sos_tap_hint),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // Warning info
            item {
                InfoCard(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.sos_info_title),
                    description = stringResource(R.string.sos_info_line_contacts) +
                            stringResource(R.string.sos_info_line_link) +
                            stringResource(R.string.sos_info_line_call) +
                            stringResource(R.string.sos_info_line_sms),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Quick actions
            item {
                Text(
                    text = stringResource(R.string.sos_quick_actions),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Filled.Phone,
                        title = stringResource(R.string.sos_call_emergency),
                        onClick = onCallEmergency,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    
                    QuickActionCard(
                        icon = Icons.Filled.Share,
                        title = stringResource(R.string.sos_share_location),
                        onClick = onShareLocation,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Emergency contacts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sos_contacts_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    TextButton(onClick = onAddContact) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.common_add))
                    }
                }
            }
            
            items(emergencyContacts) { contact ->
                EmergencyContactCard(contact = contact)
            }
            
            if (emergencyContacts.isEmpty()) {
                item {
                    EmptyContactsState(onAddContact = onAddContact)
                }
            }
        }
    }
    
    // SOS Confirmation Dialog
    if (showSOSConfirmation) {
        AlertDialog(
            onDismissRequest = { showSOSConfirmation = false },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.sos_confirm_title),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.sos_confirm_message),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTriggerSOS()
                        showSOSConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PathyColors.Error
                    )
                ) {
                    Text(stringResource(R.string.sos_confirm_send))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSOSConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.2f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmergencyContactCard(contact: EmergencyContact) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = PathyColors.Primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = PathyColors.Primary
                        )
                    }
                }
                
                Column {
                    Text(
                        text = contact.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = contact.phoneNumber,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (contact.relationship.isNotEmpty()) {
                        Text(
                            text = contact.relationship,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            IconButton(onClick = { /* Call contact */ }) {
                Icon(Icons.Filled.Phone, stringResource(R.string.common_call))
            }
        }
    }
}

@Composable
fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyContactsState(onAddContact: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PathyColors.Warning.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = PathyColors.Warning
            )
            
            Text(
                text = stringResource(R.string.sos_no_contacts_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.sos_no_contacts_body),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Button(onClick = onAddContact) {
                Text(stringResource(R.string.sos_add_contact))
            }
        }
    }
}
