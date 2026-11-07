package com.example.pathy.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.GpxImport
import com.example.pathy.data.TrailData
import com.example.pathy.offline.OfflineRegions
import com.example.pathy.offline.OfflineTrails
import com.example.pathy.offline.rememberIsOnline
import com.example.pathy.ui.design.NatureEmptyState
import com.example.pathy.ui.design.NatureLoadingState
import com.example.pathy.ui.design.NatureOfflineBanner
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.design.pathyTopBarSubtitleColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The trails kept on this phone. Everything here is read from files, so it opens with no connection. */
@Composable
fun SavedTrailsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { OfflineTrails.root(context) }
    val online by rememberIsOnline()
    var items by remember { mutableStateOf<List<OfflineTrails.Saved>?>(null) }
    var toRemove by remember { mutableStateOf<TrailData?>(null) }

    suspend fun reload() {
        items = withContext(Dispatchers.IO) { OfflineTrails.list(root) }
    }
    LaunchedEffect(Unit) { reload() }

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

    SavedTrailsContent(
        items = items,
        online = online,
        onBack = onBack,
        onOpen = { context.startActivity(TrailDetailsActivity.createIntent(context, it)) },
        onRemove = { toRemove = it },
        onImport = { importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")) }
    )

    toRemove?.let { trail ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.saved_trail_delete_one)) },
            confirmButton = {
                TextButton(onClick = {
                    toRemove = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            OfflineTrails.delete(root, trail.id)
                            runCatching { OfflineRegions.remove(trail.id) }
                        }
                        reload()
                        Toast.makeText(context, context.getString(R.string.trail_offline_removed), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.trail_offline_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/** Draws the list, the first read, and the empty list. [items] is null while the files are being read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedTrailsContent(
    items: List<OfflineTrails.Saved>?,
    online: Boolean,
    onBack: () -> Unit,
    onOpen: (TrailData) -> Unit,
    onRemove: (TrailData) -> Unit,
    onImport: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.saved_trails_title), fontWeight = FontWeight.Bold)
                        if (!items.isNullOrEmpty()) {
                            Text(
                                text = pluralStringResource(R.plurals.saved_trails_count, items.size, items.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = pathyTopBarSubtitleColor()
                            )
                        }
                    }
                },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }
            )
        }
    ) { inner ->
        when {
            items == null -> NatureLoadingState(
                message = stringResource(R.string.common_loading),
                modifier = Modifier.padding(inner).fillMaxSize()
            )

            items.isEmpty() -> Column(
                modifier = Modifier.padding(inner).fillMaxSize(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!online) NatureOfflineBanner(stringResource(R.string.offline_banner_none), Modifier.padding(horizontal = 16.dp))
                NatureEmptyState(
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.saved_trails_empty_title),
                    message = stringResource(R.string.saved_trails_empty_message)
                )
                ImportButton(onImport)
            }

            else -> LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!online) item(key = "offline") { NatureOfflineBanner(stringResource(R.string.offline_banner_saved)) }
                items(items, key = { it.trail.id }) { saved ->
                    val model = saved.trail.toCardModel()
                    TrailListCard(
                        model = model,
                        onClick = { onOpen(saved.trail) },
                        offline = true,
                        trailing = {
                            IconButton(onClick = { onRemove(saved.trail) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = stringResource(R.string.saved_trail_remove_desc, model.name),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
                item(key = "import") { ImportButton(onImport) }
            }
        }
    }
}

@Composable
private fun ImportButton(onImport: () -> Unit) {
    OutlinedButton(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp)
    ) {
        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.gpx_import))
    }
}
