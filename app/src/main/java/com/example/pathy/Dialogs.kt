package com.example.pathy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.pathy.i18n.isolateBidi
import androidx.compose.ui.res.stringResource
import kotlin.math.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import java.util.*

@Composable
fun NoTrailsDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Secondary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.common_got_it), color = PathyColors.AppBar)
                    }
                }
            },
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PathyColors.AppBar, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terrain,
                        contentDescription = null,
                        tint = PathyColors.PrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.saved_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = PathyColors.PrimaryContainer
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.saved_empty_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PathyColors.Bark,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.background
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPromptDialog(
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 8.dp,
        containerColor = PathyColors.SandContainer // גוון בהיר חמים
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PathyColors.SandContainer) // אותו צבע רקע
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = PathyColors.Primary,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.location_off_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.location_off_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onEnableClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PathyColors.Primary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Text(stringResource(R.string.location_turn_on), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_maybe_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDisabledDialog(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PathyColors.SandContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                tint = PathyColors.Accent,
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.location_off_short),
                style = MaterialTheme.typography.titleLarge.copy(color = PathyColors.Bark)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.location_needed_for_navigation),
                style = MaterialTheme.typography.bodyMedium.copy(color = PathyColors.Bark),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.AccentLight)
            ) {
                Text(stringResource(R.string.common_got_it), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun SharedTrailPopup(
    sharedByUid: String,
    trailId: String,
    onDismiss: () -> Unit,
    onViewTrail: (trailId: String) -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sharedByUid) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(sharedByUid).get()
            .addOnSuccessListener { doc ->
                username = doc.getString("name") ?: context.getString(R.string.unknown_user)
            }
            .addOnFailureListener {
                username = context.getString(R.string.unknown_user)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onViewTrail(trailId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryDark)
            ) {
                Text(stringResource(R.string.trail_go_to), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text(
                text = stringResource(R.string.shared_trail_title),
                style = MaterialTheme.typography.titleLarge,
                color = PathyColors.PrimaryDark
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.shared_trail_message, username?.isolateBidi() ?: "..."),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp
    )
}

@Composable
fun PitchDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    pitchValue: Double,
    onPitchChange: (Double) -> Unit,
    mapView: MapView?
) {
    if (showDialog) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = PathyColors.Bark,
                tonalElevation = 12.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Terrain,
                            contentDescription = null,
                            tint = PathyColors.PrimaryContainer
                        )
                        Text(
                            stringResource(R.string.angle_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = PathyColors.PrimaryContainer
                        )
                    }
                    Text(
                        "⛰️ ${pitchValue.toInt()}°",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PathyColors.PrimaryContainer
                    )
                    Slider(
                        value = pitchValue.toFloat(),
                        onValueChange = {
                            onPitchChange(it.toDouble())
                            mapView?.getMapboxMap()?.setCamera(
                                CameraOptions.Builder().pitch(it.toDouble()).build()
                            )
                        },
                        valueRange = 0f..85f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = PathyColors.SandContainer,
                            activeTrackColor = PathyColors.PrimaryLight,
                            inactiveTrackColor = PathyColors.OutlineVariant
                        )
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryLight),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            if (pitchValue != 0.0) Icons.Filled.Check else Icons.Filled.Terrain,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.angle_set), color = PathyColors.PrimaryDark)
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationTypeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSolo: () -> Unit,
    onGroup: () -> Unit
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            val dialogMaxHeight = maxHeight * 0.9f
            val circleSize = remember(maxWidth) { (maxWidth / 3f).coerceIn(96.dp, 140.dp) }
            val isNarrow = maxWidth < 360.dp
            val scroll = rememberScrollState()

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    val itemMod = if (isNarrow) Modifier.fillMaxWidth(0.9f) else Modifier.weight(1f)

                    if (isNarrow) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BigRoundAction(stringResource(R.string.nav_mode_solo),"👤", PathyColors.Primary, circleSize, itemMod, onSolo)
                            BigRoundAction(stringResource(R.string.nav_mode_group),"👥", PathyColors.Accent, circleSize, itemMod, onGroup)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BigRoundAction(stringResource(R.string.nav_mode_solo),"👤", PathyColors.Primary, circleSize, Modifier.weight(1f), onSolo)
                            BigRoundAction(stringResource(R.string.nav_mode_group),"👥", PathyColors.Accent, circleSize, Modifier.weight(1f), onGroup)
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel), color = PathyColors.Error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun GroupJoinOptionsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onFindNearby: () -> Unit,
    onCreateGroup: () -> Unit
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            val dialogMaxHeight = maxHeight * 0.9f
            val circleSize = remember(maxWidth) { (maxWidth / 3f).coerceIn(96.dp, 140.dp) }
            val isNarrow = maxWidth < 360.dp
            val scroll = rememberScrollState()

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        stringResource(R.string.nav_group_needs_group),
                        style = MaterialTheme.typography.bodyMedium.copy(color = PathyColors.Secondary)
                    )

                    if (isNarrow) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BigRoundAction(stringResource(R.string.groups_find),"🔍", PathyColors.Primary, circleSize, Modifier.fillMaxWidth(0.9f), onFindNearby)
                            BigRoundAction(stringResource(R.string.group_create),"➕", PathyColors.Accent, circleSize, Modifier.fillMaxWidth(0.9f), onCreateGroup)
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BigRoundAction(stringResource(R.string.groups_find),"🔍", PathyColors.Primary, circleSize, Modifier.weight(1f), onFindNearby)
                            BigRoundAction(stringResource(R.string.group_create),"➕", PathyColors.Accent, circleSize, Modifier.weight(1f), onCreateGroup)
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel), color = PathyColors.Error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigRoundAction(
    title: String,
    emoji: String,
    container: Color,
    circleSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = container),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(circleSize)
                .shadow(10.dp, CircleShape, clip = false)
        ) {
            Text(
                emoji,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

/** Shown instead of a screen that needs an account when nobody is signed in (a session can end while a screen is open). */
@Composable
fun SignedOutNotice() {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.error_not_signed_in),
            modifier = androidx.compose.ui.Modifier.padding(24.dp)
        )
    }
}
