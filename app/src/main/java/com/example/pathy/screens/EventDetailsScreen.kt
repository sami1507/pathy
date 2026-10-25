package com.example.pathy.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.AttendeesActivity
import com.example.pathy.CloudSavedTrailsActivity
import com.example.pathy.CommunityEvent
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// ---------- פונקציות עזר לתאריך/שעה ----------
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ממיר millis מה-DatePicker למחרוזת "dd/MM/yyyy"
private fun formatDate(millis: Long, pattern: String = "dd/MM/yyyy", locale: Locale = Locale.getDefault()): String {
    val sdf = SimpleDateFormat(pattern, locale)
    return sdf.format(Date(millis))
}

// מחזיר true אם תאריך הסיום לפני תאריך ההתחלה
private fun isEndDateBeforeStart(startDate: String, endDate: String, pattern: String = "dd/MM/yyyy"): Boolean {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    val s = runCatching { sdf.parse(startDate) }.getOrNull()
    val e = runCatching { sdf.parse(endDate) }.getOrNull()
    return s != null && e != null && e.before(s)
}

// מחזיר true אם שעת הסיום לפני/שווה לשעת ההתחלה
private fun isEndTimeBeforeOrEqualStart(startTime: String, endTime: String, pattern: String = "HH:mm"): Boolean {
    val tf = SimpleDateFormat(pattern, Locale.getDefault())
    val s = runCatching { tf.parse(startTime) }.getOrNull()
    val e = runCatching { tf.parse(endTime) }.getOrNull()
    return s != null && e != null && !e.after(s)
}
// ---------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(eventId: String?) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    var event by remember { mutableStateOf<CommunityEvent?>(null) }
    var message by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("") }
    var editEndTime by remember { mutableStateOf("") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var isTimeError by remember { mutableStateOf(false) }
    var editStartDate by remember { mutableStateOf("") }
    var editEndDate by remember { mutableStateOf("") }
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var isDateError by remember { mutableStateOf(false) }
    var editTrail by remember { mutableStateOf("") }
    var editTrailId by remember { mutableStateOf("") }
    var editRegistration by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val trailName = result.data?.getStringExtra("trailName") ?: return@rememberLauncherForActivityResult
            val trailId = result.data?.getStringExtra("trailId") ?: return@rememberLauncherForActivityResult
            editTrail = trailName
            editTrailId = trailId
        }
    }

    LaunchedEffect(eventId) {
        if (eventId != null) {
            val doc = db.collection("events").document(eventId).get().await()
            if (doc.exists()) {
                event = doc.toObject(CommunityEvent::class.java)?.copy(id = doc.id)
            }
        }
    }

    event?.let { e ->
        val currentUser = auth.currentUser
        val isCreator = currentUser?.uid == e.createdByUid
        if (!initialized) {
            editTitle = e.title
            editLocation = e.location
            editDescription = e.description
            editStartTime = e.startTime
            editEndTime = e.endTime
            editStartDate = e.startDate
            editEndDate = e.endDate
            editTrail = e.trailName
            editRegistration = e.registrationOpen
            initialized = true
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {

                if (isCreator) {
                    CardInfo(
                        title = stringResource(R.string.event_attendees_title),
                        content = pluralStringResource(R.plurals.event_registered_count, e.attendees.size, e.attendees.size),
                        editable = true
                    ) {
                        val intent = Intent(context, AttendeesActivity::class.java).apply {
                            putExtra("eventId", e.id)
                        }
                        context.startActivity(intent)
                    }
                    if (isEditing) {
                        Button(
                            onClick = {
                                if (isEndDateBeforeStart(editStartDate, editEndDate)) {  // ✅ השוואה נכונה
                                    isDateError = true
                                    return@Button
                                }
                                if (isEndTimeBeforeOrEqualStart(editStartTime, editEndTime)) { // ✅ השוואה נכונה
                                    isTimeError = true
                                    return@Button
                                }
                                val updatedEvent = e.copy(
                                    title = editTitle,
                                    location = editLocation,
                                    description = editDescription,
                                    startTime = editStartTime,
                                    endTime = editEndTime,
                                    trailName = editTrail,
                                    trailId = editTrailId,
                                    registrationOpen = editRegistration
                                )
                                db.collection("events").document(e.id).set(updatedEvent)
                                    .addOnSuccessListener {
                                        event = updatedEvent
                                        isEditing = false
                                        Toast.makeText(context, context.getString(R.string.event_updated), Toast.LENGTH_SHORT).show()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.event_save_changes)) }
                    } else {
                        Button(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.event_edit_button))
                        }
                        Button(
                            onClick = {
                                db.collection("events").document(e.id).delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(context, context.getString(R.string.event_deleted), Toast.LENGTH_SHORT).show()
                                    }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.event_delete_button), color = Color.White) }
                    }
                } else if (currentUser != null && !e.attendees.contains(currentUser.uid)) {
                    Button(
                        onClick = {
                            if (e.registrationOpen) {
                                db.collection("events").document(e.id)
                                    .update("attendees", FieldValue.arrayUnion(currentUser.uid))
                                    .addOnSuccessListener { message = context.getString(R.string.event_register_success) }
                                    .addOnFailureListener { message = context.getString(R.string.event_register_failed) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (message == "") {
                            message = if (e.registrationOpen) context.getString(R.string.event_register_cta) else context.getString(R.string.event_registration_closed)
                        }
                        Text(message, color = Color.White)
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.event_already_registered),
                            color = PathyColors.Primary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (currentUser != null) {
                            Button(
                                onClick = {
                                    db.collection("events").document(e.id)
                                        .update("attendees", FieldValue.arrayRemove(currentUser.uid))
                                        .addOnSuccessListener { message = context.getString(R.string.event_unregistered) }
                                        .addOnFailureListener { message = context.getString(R.string.event_cancel_failed) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Error)
                            ) { Text(if (message != "") message else stringResource(R.string.event_cancel_registration), color = Color.White) }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editTitle, onValueChange = { editTitle = it },
                            label = { Text(stringResource(R.string.field_title)) }, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editDescription, onValueChange = { editDescription = it },
                            label = { Text(stringResource(R.string.field_description)) }, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editLocation, onValueChange = { editLocation = it },
                            label = { Text(stringResource(R.string.field_location)) }, modifier = Modifier.fillMaxWidth()
                        )

                        CardInfo(stringResource(R.string.event_field_start_date), editStartDate, editable = true) { showStartDatePicker = true }
                        CardInfo(stringResource(R.string.event_field_end_date), editEndDate, editable = true) { showEndDatePicker = true }
                        if (isDateError) {
                            Text(stringResource(R.string.event_end_after_start_date), color = PathyColors.Error, style = MaterialTheme.typography.bodySmall)
                        }

                        CardInfo(stringResource(R.string.event_field_start_time), editStartTime, editable = true) { showStartTimePicker = true }
                        CardInfo(stringResource(R.string.event_field_end_time), editEndTime, editable = true) { showEndTimePicker = true }
                        if (isTimeError) {
                            Text(stringResource(R.string.event_end_after_start_time), color = PathyColors.Error, style = MaterialTheme.typography.bodySmall)
                        }

                        CardInfo(stringResource(R.string.event_field_trail), editTrail, editable = true) {
                            val intent = Intent(context, CloudSavedTrailsActivity::class.java).apply {
                                putExtra("isSelectionMode", true)
                                putExtra("source", "edit_event")
                            }
                            launcher.launch(intent)
                        }

                        Text(stringResource(R.string.event_field_registration))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = editRegistration, onClick = { editRegistration = true })
                                Text(stringResource(R.string.event_registration_open))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !editRegistration, onClick = { editRegistration = false })
                                Text(stringResource(R.string.event_registration_closed_short))
                            }
                        }
                    } else {
                        Text(
                            text = e.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PathyColors.Primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        CardInfo(stringResource(R.string.event_field_description), e.description)
                        CardInfo(stringResource(R.string.event_field_dates), "${e.startDate} - ${e.endDate}")
                        CardInfo(stringResource(R.string.event_field_times), "${e.startTime} - ${e.endTime}")
                        CardInfo(stringResource(R.string.event_field_location), e.location)
                        CardInfo(stringResource(R.string.event_field_trail), "${e.trailName} (⭐ ${e.trailRating})")
                        CardInfo(stringResource(R.string.event_field_registration), if (e.registrationOpen) stringResource(R.string.event_registration_open) else stringResource(R.string.event_registration_closed_short))
                    }
                }

                if (showStartDatePicker) {
                    AlertDialog(
                        onDismissRequest = { showStartDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                startDatePickerState.selectedDateMillis?.let {
                                    editStartDate = formatDate(it) // ✅ עכשיו קיימת
                                    isDateError = false
                                }
                                showStartDatePicker = false
                            }) { Text(stringResource(R.string.common_ok), color = PathyColors.Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartDatePicker = false }) { Text(stringResource(R.string.common_cancel), color = PathyColors.Secondary) }
                        },
                        title = { Text(stringResource(R.string.event_pick_start_date)) },
                        text = { DatePicker(state = startDatePickerState) }
                    )
                }
                if (showEndDatePicker) {
                    AlertDialog(
                        onDismissRequest = { showEndDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                endDatePickerState.selectedDateMillis?.let {
                                    editEndDate = formatDate(it) // ✅ עכשיו קיימת
                                    isDateError = false
                                }
                                showEndDatePicker = false
                            }) { Text(stringResource(R.string.common_ok), color = PathyColors.Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndDatePicker = false }) { Text(stringResource(R.string.common_cancel), color = PathyColors.Secondary) }
                        },
                        title = { Text(stringResource(R.string.event_pick_end_date)) },
                        text = { DatePicker(state = endDatePickerState) }
                    )
                }
                if (showStartTimePicker) {
                    val timePickerState = rememberTimePickerState()
                    AlertDialog(
                        onDismissRequest = { showStartTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val hour = timePickerState.hour
                                val minute = timePickerState.minute
                                // ✅ ציון Locale כדי למנוע אזהרה
                                editStartTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                isTimeError = false
                                showStartTimePicker = false
                            }) { Text(stringResource(R.string.common_ok), color = PathyColors.Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showStartTimePicker = false }) { Text(stringResource(R.string.common_cancel), color = PathyColors.Secondary) }
                        },
                        title = { Text(stringResource(R.string.event_pick_start_time)) },
                        text = { TimePicker(state = timePickerState) }
                    )
                }
                if (showEndTimePicker) {
                    val timePickerState = rememberTimePickerState()
                    AlertDialog(
                        onDismissRequest = { showEndTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val hour = timePickerState.hour
                                val minute = timePickerState.minute
                                // ✅ ציון Locale כדי למנוע אזהרה
                                editEndTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                isTimeError = false
                                showEndTimePicker = false
                            }) { Text(stringResource(R.string.common_ok), color = PathyColors.Primary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEndTimePicker = false }) { Text(stringResource(R.string.common_cancel), color = PathyColors.Secondary) }
                        },
                        title = { Text(stringResource(R.string.event_pick_end_time)) },
                        text = { TimePicker(state = timePickerState) }
                    )
                }
            }
        }
    } ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PathyColors.Primary)
        }
    }
}

@Composable
fun CardInfo(title: String, content: String, editable: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = editable, onClick = { onClick?.invoke() })
            .then(if (editable) Modifier.background(PathyColors.Highlight) else Modifier)
            .padding(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = content, style = MaterialTheme.typography.bodyMedium)
    }
}
