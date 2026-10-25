package com.example.pathy.screens

import com.example.pathy.SignedOutNotice
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.CommunityActivity
import com.example.pathy.NoTrailsDialog
import com.example.pathy.data.ImagePickerSection
import com.example.pathy.data.ImageSourceBottomSheet
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.createImageUri
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.*

// ⬇️ AI Moderation (שלב 3)
import com.example.pathy.data.moderation.ModerationDecision
import com.example.pathy.data.moderation.ModerationPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    selectedTrail: SavedTrail?,
    onSelectTrailClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid
    if (userId == null) {
        SignedOutNotice()
        return
    }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var startDateText by remember { mutableStateOf("") }
    var endDateText by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var isRegistrationOpen by remember { mutableStateOf(true) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showNoTrailsDialog by remember { mutableStateOf(false) }
    var dateTimeError by remember { mutableStateOf("") }
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()
    val calendar = Calendar.getInstance()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris != null) selectedImages = uris.take(5) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { selectedImages = (selectedImages + it).take(5) }
    }

    NoTrailsDialog(
        show = showNoTrailsDialog,
        onDismiss = { showNoTrailsDialog = false }
    )

    BackHandler {
        showExitDialog = true
    }

    Scaffold(
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
                        text = stringResource(R.string.community_new_event),
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
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // כותרת האירוע
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = PathyColors.Primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text(stringResource(R.string.event_title_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PathyColors.Primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }

                    // תיאור האירוע
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = PathyColors.Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.field_description), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = description,
                                onValueChange = {
                                    if (it.length <= 500) description = it
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PathyColors.Primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            Text(
                                text = "${description.length} / 500",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    // מיקום האירוע
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = PathyColors.Primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text(stringResource(R.string.event_location_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PathyColors.Primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }

                    // תאריכים
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DateRange, contentDescription = null, tint = PathyColors.Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.event_dates_header), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable { showStartDatePicker = true }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = stringResource(R.string.event_start_date, if (startDateText.isEmpty()) stringResource(R.string.common_choose) else startDateText),
                                    style = MaterialTheme.typography.bodyLarge.copy(color = PathyColors.Primary)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable { showEndDatePicker = true }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = stringResource(R.string.event_end_date, if (endDateText.isEmpty()) stringResource(R.string.common_choose) else endDateText),
                                    style = MaterialTheme.typography.bodyLarge.copy(color = PathyColors.Primary)
                                )
                            }
                        }
                    }

                    // שעות
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = PathyColors.Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.event_times_header), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable {
                                        TimePickerDialog(
                                            context,
                                            { _, h, m -> startTime = String.format("%02d:%02d", h, m) },
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE),
                                            true
                                        ).show()
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(stringResource(R.string.event_start_time, if (startTime.isEmpty()) stringResource(R.string.common_choose) else startTime), style = MaterialTheme.typography.bodyLarge.copy(color = PathyColors.Primary))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable {
                                        TimePickerDialog(
                                            context,
                                            { _, h, m -> endTime = String.format("%02d:%02d", h, m) },
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE),
                                            true
                                        ).show()
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(stringResource(R.string.event_end_time, if (endTime.isEmpty()) stringResource(R.string.common_choose) else endTime), style = MaterialTheme.typography.bodyLarge.copy(color = PathyColors.Primary))
                            }
                        }
                    }

                    // בחירת מסלול
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Map, contentDescription = null, tint = PathyColors.Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.field_trail), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            db.collection("users")
                                                .document(userId)
                                                .collection("trails_user")
                                                .get()
                                                .addOnSuccessListener { result ->
                                                    if (!result.isEmpty) {
                                                        onSelectTrailClick()
                                                    } else {
                                                        showNoTrailsDialog = true
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.trails_load_failed_retry),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = selectedTrail?.name ?: stringResource(R.string.select_trail),
                                    style = MaterialTheme.typography.bodyLarge.copy(color = PathyColors.Primary)
                                )
                            }
                        }
                    }

                    // בחירת תמונות
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Image, contentDescription = null, tint = PathyColors.Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.field_photos), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            ImagePickerSection(
                                imageUris = selectedImages,
                                onPickImages = { showImageSourceDialog = true },
                                onRemoveImage = { uri -> selectedImages = selectedImages - uri }
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PathyColors.Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.event_registration_header), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.event_registration_open_q), style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = isRegistrationOpen,
                                    onCheckedChange = { isRegistrationOpen = it },
                                    thumbContent = {
                                        if (isRegistrationOpen) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                            val isValid = try {
                                val start = dateFormat.parse("$startDateText $startTime")
                                val end = dateFormat.parse("$endDateText $endTime")

                                if (start == null || end == null) {
                                    dateTimeError = context.getString(R.string.event_datetime_invalid)
                                    false
                                } else if (startDateText == endDateText && start.after(end)) {
                                    dateTimeError = context.getString(R.string.event_start_time_before_end)
                                    false
                                } else if (start.after(end)) {
                                    dateTimeError = context.getString(R.string.event_start_date_before_end)
                                    false
                                } else {
                                    dateTimeError = ""
                                    true
                                }
                            } catch (e: Exception) {
                                dateTimeError = context.getString(R.string.event_datetimes_invalid)
                                false
                            }

                            when {
                                title.isBlank() -> Toast.makeText(context, context.getString(R.string.error_title_required), Toast.LENGTH_SHORT).show()
                                description.isBlank() -> Toast.makeText(context, context.getString(R.string.error_description_required), Toast.LENGTH_SHORT).show()
                                location.isBlank() -> Toast.makeText(context, context.getString(R.string.error_location_required), Toast.LENGTH_SHORT).show()
                                startDateText.isBlank() -> Toast.makeText(context, context.getString(R.string.error_start_date_required), Toast.LENGTH_SHORT).show()
                                endDateText.isBlank() -> Toast.makeText(context, context.getString(R.string.error_end_date_required), Toast.LENGTH_SHORT).show()
                                startTime.isBlank() -> Toast.makeText(context, context.getString(R.string.error_start_time_required), Toast.LENGTH_SHORT).show()
                                endTime.isBlank() -> Toast.makeText(context, context.getString(R.string.error_end_time_required), Toast.LENGTH_SHORT).show()
                                selectedTrail == null -> Toast.makeText(context, context.getString(R.string.error_select_trail), Toast.LENGTH_SHORT).show()
                                !isValid -> Unit
                                else -> {
                                    // ⬇️ בדיקת AI לפני יצירת האירוע (כמו בפוסטים)
                                    coroutineScope.launch {
                                        val textToCheck = listOf(title, description, location)
                                            .filter { it.isNotBlank() }
                                            .joinToString("\n")

                                        val decision = try {
                                            withContext(Dispatchers.IO) {
                                                ModerationPipeline.moderate(textToCheck)
                                            }
                                        } catch (_: Exception) {
                                            // במקרה של תקלה ברשת – נמשיך “fail-open” (יש לך סינון מקומי בפייפליין)
                                            ModerationDecision.OK
                                        }

                                        when (decision) {
                                            ModerationDecision.BLOCKED -> {
                                                Toast.makeText(context, context.getString(R.string.moderation_event_blocked), Toast.LENGTH_LONG).show()
                                            }
                                            ModerationDecision.NEEDS_EDIT -> {
                                                Toast.makeText(context, context.getString(R.string.moderation_rephrase), Toast.LENGTH_LONG).show()
                                            }
                                            ModerationDecision.OK -> {
                                                val intent = Intent(context, CommunityActivity::class.java).apply {
                                                    putExtra("fromEvent", true)
                                                    putExtra("eventTitle", title)
                                                    putExtra("eventDescription", description)
                                                    putExtra("eventStartDate", startDateText)
                                                    putExtra("eventEndDate", endDateText)
                                                    putExtra("eventStartTime", startTime)
                                                    putExtra("eventEndTime", endTime)
                                                    putExtra("eventLocation", location)
                                                    putExtra("trailId", selectedTrail?.id ?: "unknown")
                                                    putExtra("trailName", selectedTrail?.name ?: "")
                                                    putExtra("trailRating", selectedTrail?.rating ?: 0)
                                                    putExtra("isRegistrationOpen", isRegistrationOpen)
                                                    putStringArrayListExtra("eventImageUris", ArrayList(selectedImages.map { it.toString() }))
                                                }
                                                context.startActivity(intent)
                                                (context as? Activity)?.finish()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
                    ) {
                        Text(stringResource(R.string.event_create_button), color = Color.White)
                    }

                    if (dateTimeError.isNotEmpty()) {
                        Text(
                            text = dateTimeError,
                            color = PathyColors.Error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                }
            }
        }
    }

    if (showStartDatePicker) {
        AlertDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { startDateText = formatDate(it) }
                    showStartDatePicker = false
                }) { Text(stringResource(R.string.common_ok), color = PathyColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel), color = PathyColors.Secondary)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = PathyColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.event_pick_start_date), style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    color = Color.White
                ) {
                    DatePicker(state = startDatePickerState)
                }
            }
        )
    }

    if (showEndDatePicker) {
        AlertDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDatePickerState.selectedDateMillis?.let { endDateText = formatDate(it) }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.common_ok), color = PathyColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel), color = PathyColors.Secondary)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = PathyColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.event_pick_end_date), style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    color = Color.White
                ) {
                    DatePicker(state = endDatePickerState)
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? Activity)?.finish()
                }) {
                    Text(stringResource(R.string.common_discard), color = PathyColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.common_continue), color = PathyColors.Secondary)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = PathyColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.confirm_exit_title), style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Text(stringResource(R.string.event_discard_confirm), style = MaterialTheme.typography.bodyLarge)
            }
        )
    }

    if (showImageSourceDialog) {
        ImageSourceBottomSheet(
            onDismiss = { showImageSourceDialog = false },
            onPickGallery = {
                showImageSourceDialog = false
                galleryLauncher.launch("image/*")
            },
            onPickCamera = {
                showImageSourceDialog = false
                val uri = createImageUri(context)
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            }
        )
    }
}

fun formatDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}
