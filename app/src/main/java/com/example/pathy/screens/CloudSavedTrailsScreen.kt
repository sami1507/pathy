package com.example.pathy.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.FirebaseRepository.getTrailsByIds
import com.example.pathy.data.FirebaseRepository.getUserTrailIds
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.toTrailData
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun CloudSavedTrailsScreen(
    isSelectionMode: Boolean,
    source: String? = null
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val cloudTrails = remember { mutableStateListOf<SavedTrail>() }
    var isDeleteMode by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    val selectedTrails = remember { mutableStateListOf<SavedTrail>() }

    var loadFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry) {
        isLoading = true
        loadFailed = false
        try {
        val user = FirebaseAuth.getInstance().currentUser
        user?.uid?.let { uid ->
            val trailIds = getUserTrailIds(uid, propagateFailure = true)
            val trails = getTrailsByIds(trailIds)
            cloudTrails.clear()
            cloudTrails.addAll(trails)
        }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { loadFailed = true
        } finally { isLoading = false }
    }

    LaunchedEffect(selectedTrails.size) {
        isDeleteMode = selectedTrails.isNotEmpty()
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.trails_loading), style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else if (loadFailed) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(androidx.compose.ui.res.stringResource(com.example.pathy.R.string.load_failed))
            TextButton(onClick = { retry++ }) { Text(androidx.compose.ui.res.stringResource(com.example.pathy.R.string.retry)) }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.saved_trails_title),
                    style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)

                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isDeleteMode) {
                    Button(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(pluralStringResource(R.plurals.delete_trails_count, selectedTrails.size, selectedTrails.size), color = Color.White)
                    }
                }
            }

            items(cloudTrails) { trail ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(
                            width = if (selectedTrails.contains(trail)) 3.dp else 0.dp,
                            color = if (selectedTrails.contains(trail)) PathyColors.Error else PathyColors.Primary,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (!selectedTrails.contains(trail)) {
                                        selectedTrails.add(trail)
                                    }
                                },
                                onTap = {
                                    if (selectedTrails.isNotEmpty()) {
                                        if (selectedTrails.contains(trail)) selectedTrails.remove(trail)
                                        else selectedTrails.add(trail)
                                    } else {
                                        if (isSelectionMode) {
                                            val resultIntent = Intent().apply {
                                                putExtra("selectedTrailJson", Gson().toJson(trail))
                                                putExtra("trailName", trail.name)
                                                putExtra("trailId", trail.id)
                                                putExtra("source", source)
                                            }
                                            (context as Activity).setResult(Activity.RESULT_OK, resultIntent)
                                            context.finish()
                                        } else {
                                            val trailData = trail.toTrailData()
                                            val intent    = TrailDetailsActivity.createIntent(context, trailData)
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "📍 ${trail.name} , ${trail.locationName}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.trail_rating_line, trail.rating),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.trail_distance_line, trail.distanceInMeters),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.trail_difficulty_line, trail.difficulty),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid
                    val db = FirebaseFirestore.getInstance()
                    selectedTrails.forEach { trail ->
                        db.collection("users")
                            .document(userId ?: return@forEach)
                            .collection("trails_user")
                            .document(trail.id)
                            .delete()
                    }
                    cloudTrails.removeAll(selectedTrails)
                    selectedTrails.clear()
                    showConfirmDelete = false
                }) {
                    Text(stringResource(R.string.common_delete), color = PathyColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.saved_trails_delete_confirm)) }
        )
    }
}