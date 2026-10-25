package com.example.pathy

import androidx.compose.ui.res.stringResource
import com.example.pathy.i18n.PathyActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pathy.emergency.EmergencyContact
import com.example.pathy.emergency.EmergencyContactStore
import com.example.pathy.emergency.EmergencySOS
import com.example.pathy.screens.EmergencySOSScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import kotlinx.coroutines.launch

/**
 * 🆘 Emergency SOS: manage emergency contacts, send SOS SMS, dial emergency services.
 */
class EmergencySOSActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                EmergencySOSRoute(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun EmergencySOSRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sos = remember { EmergencySOS(context) }
    var contacts by remember { mutableStateOf(EmergencyContactStore.load(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingSmsAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Runs an SMS-sending action only once SEND_SMS is granted (asks the user if needed)
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingSmsAction
        pendingSmsAction = null
        if (granted && action != null) {
            action()
        } else {
            Toast.makeText(context, context.getString(R.string.sos_sms_permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    fun withSmsPermission(action: () -> Unit) {
        if (contacts.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.sos_add_contact_first), Toast.LENGTH_SHORT).show()
            showAddDialog = true
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingSmsAction = action
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    EmergencySOSScreen(
        emergencyContacts = contacts,
        onTriggerSOS = {
            withSmsPermission {
                scope.launch {
                    sos.triggerSOS(contacts = contacts)
                        .onSuccess { Toast.makeText(context, context.getString(R.string.sos_sent), Toast.LENGTH_LONG).show() }
                        .onFailure { e -> Toast.makeText(context, context.getString(R.string.sos_send_failed, e.message), Toast.LENGTH_LONG).show() }
                }
            }
        },
        onCallEmergency = { sos.callEmergencyServices("112") },
        onShareLocation = {
            withSmsPermission {
                scope.launch {
                    sos.shareCurrentLocation(contacts = contacts)
                        .onSuccess { Toast.makeText(context, context.getString(R.string.sos_location_shared), Toast.LENGTH_LONG).show() }
                        .onFailure { e -> Toast.makeText(context, context.getString(R.string.sos_share_failed, e.message), Toast.LENGTH_LONG).show() }
                }
            }
        },
        onAddContact = { showAddDialog = true },
        onBack = onBack
    )

    if (showAddDialog) {
        AddEmergencyContactDialog(
            onDismiss = { showAddDialog = false },
            onSave = { contact ->
                contacts = contacts + contact
                EmergencyContactStore.save(context, contacts)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddEmergencyContactDialog(
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sos_contact_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.field_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanPhone = phone.trim()
                when {
                    name.isBlank() -> Toast.makeText(context, context.getString(R.string.error_name_required), Toast.LENGTH_SHORT).show()
                    cleanPhone.count { it.isDigit() } < 7 -> Toast.makeText(context, context.getString(R.string.error_phone_invalid), Toast.LENGTH_SHORT).show()
                    else -> onSave(EmergencyContact(name = name.trim(), phoneNumber = cleanPhone))
                }
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
