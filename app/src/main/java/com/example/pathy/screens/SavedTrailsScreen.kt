package com.example.pathy.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.GpxImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.theme.PathyColors

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pathy.OfflineTrailDetailsActivity
import com.example.pathy.data.SavedTrail
import com.example.pathy.offline.OfflineRegions
import com.google.gson.Gson
import java.io.File

@Composable
fun SavedTrailsScreen() {
    val context = LocalContext.current
    var savedTrails by remember { mutableStateOf<List<Pair<String, SavedTrail>>>(emptyList()) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun loadTrails() {
        val trailsDir = File(context.filesDir, "trails")
        if (!trailsDir.exists()) {
            savedTrails = emptyList()
            return
        }

        val gson = Gson()
        savedTrails = trailsDir.listFiles()?.mapNotNull { dir ->
            if (dir.isDirectory) {
                val trailFile = File(dir, "trail.json")
                if (trailFile.exists()) {
                    try {
                        val content = trailFile.readText()
                        val trail = gson.fromJson(content, SavedTrail::class.java)
                        dir.name to trail
                    } catch (_: Exception) {
                        null
                    }
                } else null
            } else null
        } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        loadTrails()
    }

    LaunchedEffect(selectedFiles.size) {
        isDeleteMode = selectedFiles.isNotEmpty()
    }

    // A GPX file from a website, a friend or another app becomes a trail that can be followed.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val trail = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: error("unreadable")
                    input.use { GpxImport.toTrailData(GpxImport.read(it)) }
                }
            }
            trail.onSuccess { context.startActivity(TrailDetailsActivity.createIntent(context, it)) }
                .onFailure { e ->
                    val tooShort = e is IllegalArgumentException && e.message?.contains("short") == true
                    Toast.makeText(
                        context,
                        context.getString(if (tooShort) R.string.gpx_import_too_short else R.string.gpx_import_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.saved_trails_title), style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.gpx_import))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isDeleteMode) {
            Button(
                onClick = { showConfirmDelete = true },
                colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(pluralStringResource(R.plurals.delete_trails_count, selectedFiles.size, selectedFiles.size), color = Color.White)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(savedTrails) { index, (fileName, trail) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (!selectedFiles.contains(fileName)) {
                                        selectedFiles.add(fileName)
                                    }
                                },
                                onTap = {
                                    if (selectedFiles.isNotEmpty()) {
                                        if (selectedFiles.contains(fileName)) selectedFiles.remove(fileName)
                                        else selectedFiles.add(fileName)
                                    } else {
                                        val trailDir = File(context.filesDir, "trails/${trail.id}")
                                        val trailJsonFile = File(trailDir, "trail.json")

                                        val intent = Intent(context, OfflineTrailDetailsActivity::class.java).apply {
                                            putExtra("offlineTrailPath", trailJsonFile.absolutePath)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        },
                    border = if (selectedFiles.contains(fileName)) BorderStroke(2.dp, PathyColors.Error) else null,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(trail.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.saved_trail_summary, stringResource(R.string.unit_km, "%.1f".format(trail.distanceInMeters / 1000.0)), stringResource(R.string.duration_minutes, trail.durationInMillis / 60000)), style = MaterialTheme.typography.bodySmall)
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
                    val toDelete = selectedFiles.toList()
                    toDelete.forEach { fileKey ->
                        val offlineDir = File(context.filesDir, "trails/$fileKey")
                        if (offlineDir.exists() && offlineDir.isDirectory) {
                            offlineDir.deleteRecursively()
                        }
                        runCatching { OfflineRegions.remove(fileKey) }
                    }
                    selectedFiles.clear()
                    showConfirmDelete = false
                    loadTrails()
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

