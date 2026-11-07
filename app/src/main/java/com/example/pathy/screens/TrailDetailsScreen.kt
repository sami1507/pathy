package com.example.pathy.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.pathy.R
import com.example.pathy.TrailNavigationActivity
import com.example.pathy.bookmarkDataFor
import com.example.pathy.bookmarks.BookmarkManager
import com.example.pathy.calculateBounds
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailReplayActivity
import com.example.pathy.data.from
import com.example.pathy.formatDifficulty
import com.example.pathy.formatDistance
import com.example.pathy.getDisplayTrailName
import com.example.pathy.i18n.TrailInfoLabels
import com.example.pathy.offline.OfflineRegions
import com.example.pathy.offline.OfflineTrails
import com.example.pathy.offline.rememberIsOnline
import com.example.pathy.trailClimb
import com.example.pathy.ui.design.MountainHero
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NatureOfflineBanner
import com.example.pathy.ui.design.NatureSectionHeader
import com.example.pathy.ui.design.NatureStatTile
import com.example.pathy.ui.design.NatureTag
import com.example.pathy.ui.design.difficultyColor
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.TrailLayers
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.attribution.AttributionPlugin
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Whether the trail is kept on this phone, so it can be followed with no signal. */
internal sealed interface OfflineSave {
    data object NotSaved : OfflineSave
    data class Saving(val progress: Float) : OfflineSave

    /** [sizeText] is null when the size of the saved map could not be read. */
    data class Saved(val sizeText: String?) : OfflineSave
}

internal class TrailDetailsActions(
    val onStart: () -> Unit = {},
    val onPreview: () -> Unit = {},
    val onSaveOffline: () -> Unit = {},
    val onRemoveOffline: () -> Unit = {},
    val onBookmark: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onAddToMine: () -> Unit = {}
)

/**
 * A trail, the way a hiker needs to read it: what it is, where it runs, whether it is kept on the
 * phone, and one clear button to start. The map and the live conditions are passed in, so the layout
 * can be drawn and tested without Mapbox or Firebase.
 */
@Composable
internal fun TrailDetailsContent(
    trail: TrailData,
    title: String,
    gainText: String?,
    maxText: String?,
    elevationLoading: Boolean,
    offlineSave: OfflineSave,
    online: Boolean,
    bookmarked: Boolean,
    actions: TrailDetailsActions,
    map: @Composable () -> Unit,
    status: @Composable () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = actions.onStart,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.trail_action_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) {
            MountainHero(
                title = title,
                eyebrow = stringResource(R.string.field_trail),
                subtitle = trail.locationName.takeIf { it.isNotBlank() },
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NatureTag(label = formatDifficulty(context, trail.difficulty), color = difficultyColor(trail.difficulty), onHero = true)
                        if (offlineSave is OfflineSave.Saved) {
                            NatureTag(label = stringResource(R.string.trail_tag_offline), color = PathyColors.Primary, icon = Icons.Filled.CloudDone, onHero = true)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NatureStatTile(Icons.Filled.Straighten, formatDistance(context, trail.distanceInMeters), stringResource(R.string.stat_length), Modifier.weight(1f))
                        NatureStatTile(Icons.Filled.Timer, durationText(trail.durationInMillis), stringResource(R.string.stat_time), Modifier.weight(1f))
                        NatureStatTile(Icons.Filled.TrendingUp, gainText ?: "–", stringResource(R.string.stat_elevation), Modifier.weight(1f))
                    }
                }
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!online) {
                    NatureOfflineBanner(
                        message = if (offlineSave is OfflineSave.Saved) stringResource(R.string.trail_offline_saved_title) else null
                    )
                }
                MapCard(onPreview = actions.onPreview, map = map)
                OfflineCard(offlineSave, online, actions)
                if (online) status()

                NatureSectionHeader(title = stringResource(R.string.trail_details_header))
                NatureCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    DetailRow(
                        label = stringResource(R.string.stat_elevation_gain),
                        value = if (elevationLoading) stringResource(R.string.common_loading) else gainText ?: stringResource(R.string.common_unavailable)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        label = stringResource(R.string.stat_max_elevation),
                        value = if (elevationLoading) stringResource(R.string.common_loading) else maxText ?: stringResource(R.string.common_unavailable)
                    )
                }

                NatureSectionHeader(title = stringResource(R.string.trail_actions_title))
                KeepAndShare(online, bookmarked, actions)
                if (!online) {
                    Text(
                        text = stringResource(R.string.trail_needs_connection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MapCard(onPreview: () -> Unit, map: @Composable () -> Unit) {
    val description = stringResource(R.string.trail_map_description)
    val previewLabel = stringResource(R.string.trail_preview)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .semantics { contentDescription = description }
    ) {
        map()
        // The map is a picture of the trail inside a scrolling page, so it does not take drags away from the page.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = previewLabel, role = Role.Button, onClick = onPreview)
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).clearAndSetSemantics { },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PathyColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(previewLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun OfflineCard(state: OfflineSave, online: Boolean, actions: TrailDetailsActions) {
    NatureCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state is OfflineSave.Saved) Icons.Filled.CloudDone else Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (state) {
                    is OfflineSave.Saved -> {
                        Text(stringResource(R.string.trail_offline_saved_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.sizeText?.let { stringResource(R.string.trail_offline_saved_detail, it) }
                                ?: stringResource(R.string.trail_offline_saved_detail_nosize),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = actions.onRemoveOffline,
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.trail_offline_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    is OfflineSave.Saving -> {
                        val percent = (state.progress * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = stringResource(R.string.trail_offline_saving, percent),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { contentDescription = "$percent%" }
                        )
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }

                    OfflineSave.NotSaved -> {
                        Text(stringResource(R.string.trail_offline_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(if (online) R.string.trail_offline_save_detail else R.string.trail_offline_needs_connection),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (online) {
                            Button(
                                onClick = actions.onSaveOffline,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.trail_offline_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeepAndShare(online: Boolean, bookmarked: Boolean, actions: TrailDetailsActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(
            icon = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            label = stringResource(if (bookmarked) R.string.bookmark_remove_button else R.string.bookmark_add_button),
            enabled = online,
            onClick = actions.onBookmark
        )
        ActionButton(Icons.Filled.Add, stringResource(R.string.trail_save_button), online, actions.onAddToMine)
        ActionButton(Icons.Filled.Share, stringResource(R.string.trail_action_share), online, actions.onShare)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

// ---- the screen: connects the layout above to the phone, the network and Firebase -------------------

@Composable
fun TrailDetailsScreen(trail: TrailData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val online by rememberIsOnline()
    val root = remember { OfflineTrails.root(context) }

    var title by remember { mutableStateOf(TrailInfoLabels.listName(context, trail.name, trail.info)) }
    var tags by remember { mutableStateOf(trail.tags) }
    var elevationLoading by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf<OfflineSave>(OfflineSave.NotSaved) }
    var confirmRemove by remember { mutableStateOf(false) }
    var bookmarked by remember { mutableStateOf(false) }

    // What was saved on this phone, read from files: it needs no connection.
    LaunchedEffect(trail.id) {
        val saved = withContext(Dispatchers.IO) { OfflineTrails.isSaved(root, trail.id) }
        if (saved) offline = OfflineSave.Saved(savedSizeText(context, trail.id))
    }

    // An unnamed trail is named after the place it runs through. That needs a lookup, so it waits for a connection.
    LaunchedEffect(trail.id, online) {
        if (online) title = withContext(Dispatchers.IO) { getDisplayTrailName(context, trail) }
    }

    // Climb and highest point come from terrain data. A trail saved earlier already carries them.
    LaunchedEffect(trail.id, online) {
        if ((tags.elevationGain == null || tags.maxElevation == null) && online) {
            elevationLoading = true
            try {
                trailClimb(trail.geoPoints)?.let { (gain, max) -> tags = tags.copy(elevationGain = gain, maxElevation = max) }
            } finally {
                elevationLoading = false
            }
        }
    }

    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val bookmarks = remember { BookmarkManager() }
    LaunchedEffect(uid, trail.id, online) {
        if (uid != null && online) bookmarks.loadBookmarks(uid).onSuccess { bookmarked = bookmarks.isBookmarked(trail.id) }
    }

    val actions = TrailDetailsActions(
        onStart = { startGuidance(context, trail) },
        onPreview = {
            context.startActivity(Intent(context, TrailReplayActivity::class.java).putExtra("trailJson", Gson().toJson(trail)))
        },
        onSaveOffline = {
            if (!online) {
                Toast.makeText(context, context.getString(R.string.trail_offline_needs_connection), Toast.LENGTH_LONG).show()
            } else {
                scope.launch {
                    offline = OfflineSave.Saving(0f)
                    val ok = saveTrailToOffline(context, trail.copy(tags = tags)) { offline = OfflineSave.Saving(it) }
                    if (ok) {
                        offline = OfflineSave.Saved(savedSizeText(context, trail.id))
                        Toast.makeText(context, context.getString(R.string.trail_saved_offline), Toast.LENGTH_SHORT).show()
                    } else {
                        offline = OfflineSave.NotSaved
                        Toast.makeText(context, context.getString(R.string.trail_offline_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        },
        onRemoveOffline = { confirmRemove = true },
        onBookmark = {
            if (uid == null) {
                Toast.makeText(context, context.getString(R.string.bookmarks_sign_in_to_save), Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    bookmarks.toggleBookmark(uid, trail.id, trail.name, bookmarkDataFor(trail))
                        .onSuccess {
                            bookmarked = it
                            Toast.makeText(context, context.getString(if (it) R.string.bookmark_added else R.string.bookmark_removed), Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { e ->
                            Toast.makeText(context, context.getString(R.string.error_generic_with_message, e.message), Toast.LENGTH_SHORT).show()
                        }
                }
            }
        },
        onShare = { shareTrail(context, trail.copy(tags = tags)) },
        onAddToMine = { addToMyTrails(context, trail) }
    )

    TrailDetailsContent(
        trail = trail,
        title = title,
        gainText = tags.elevationGain?.let { context.getString(R.string.unit_m, it.toString()) },
        maxText = tags.maxElevation?.let { context.getString(R.string.unit_m, it.toString()) },
        elevationLoading = elevationLoading,
        offlineSave = offline,
        online = online,
        bookmarked = bookmarked,
        actions = actions,
        map = { TrailMap(trail) },
        status = { TrailStatusSection(trail) }
    )

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.saved_trail_delete_one)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            OfflineTrails.delete(root, trail.id)
                            runCatching { OfflineRegions.remove(trail.id) }
                        }
                        offline = OfflineSave.NotSaved
                        Toast.makeText(context, context.getString(R.string.trail_offline_removed), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.trail_offline_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

private suspend fun savedSizeText(context: Context, id: String): String? =
    OfflineRegions.sizeBytes(id)?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }

/**
 * Keeps the trail on the phone: first the map around it (Mapbox stores it), then the trail itself. The
 * trail is written last, so a download that is interrupted never leaves a trail whose map is missing.
 * Returns false on any failure.
 */
suspend fun saveTrailToOffline(context: Context, trail: TrailData, onProgress: (Float) -> Unit): Boolean {
    if (trail.geoPoints.size < 2) return false
    val downloaded = OfflineRegions.download(trail.id, trail.geoPoints, onProgress)
    if (!downloaded) return false
    return runCatching {
        withContext(Dispatchers.IO) { OfflineTrails.save(OfflineTrails.root(context), trail) }
    }.isSuccess
}

/** Opens turn-by-turn guidance. It works from the saved line and the GPS, so it needs no connection. */
@SuppressLint("MissingPermission")
private fun startGuidance(context: Context, trail: TrailData) {
    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasLocation) {
        Toast.makeText(context, context.getString(R.string.trail_location_permission_needed), Toast.LENGTH_LONG).show()
        return
    }
    fun open(lat: Double, lng: Double) {
        context.startActivity(
            Intent(context, TrailNavigationActivity::class.java)
                .putExtra("lat", lat)
                .putExtra("lng", lng)
                .putExtra("trail", Gson().toJson(trail))
        )
    }
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            open(location.latitude, location.longitude)
        } else {
            // No recent position yet (the phone has just woken up, or GPS is still searching).
            // Guidance finds the position itself, so open it at the start of the trail instead of refusing.
            val first = trail.geoPoints.firstOrNull()
            if (first == null) {
                Toast.makeText(context, context.getString(R.string.trail_location_unavailable), Toast.LENGTH_LONG).show()
            } else {
                open(first.latitude, first.longitude)
            }
        }
    }
}

private fun addToMyTrails(context: Context, trail: TrailData) {
    val user = FirebaseAuth.getInstance().currentUser
    if (user == null) {
        Toast.makeText(context, context.getString(R.string.error_not_signed_in), Toast.LENGTH_SHORT).show()
        return
    }
    FirebaseFirestore.getInstance().collection("users").document(user.uid)
        .collection("trails_user").document(trail.id)
        .set(mapOf("trailId" to trail.id))
        .addOnSuccessListener { Toast.makeText(context, context.getString(R.string.trail_saved), Toast.LENGTH_SHORT).show() }
        .addOnFailureListener { Toast.makeText(context, context.getString(R.string.trail_save_error), Toast.LENGTH_SHORT).show() }
}

private fun shareTrail(context: Context, trail: TrailData) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    if (userId == null) {
        Toast.makeText(context, context.getString(R.string.error_not_signed_in), Toast.LENGTH_SHORT).show()
        return
    }
    val ref = FirebaseFirestore.getInstance().collection("trails").document(trail.id)
    ref.get().addOnSuccessListener { snapshot ->
        if (snapshot.exists()) {
            shareLink(context, userId, trail.id)
        } else {
            ref.set(from(trail, userId))
                .addOnSuccessListener { shareLink(context, userId, trail.id) }
                .addOnFailureListener { Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show() }
        }
    }.addOnFailureListener { Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show() }
}

private fun shareLink(context: Context, sharedBy: String, trailId: String) {
    val link = "https://tempathy-e0c4b.web.app/trail?sharedBy=$sharedBy&trailId=$trailId"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_trail_message, link))
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_chooser_title)))
}

/**
 * The trail drawn on the outdoors map. The map is saved by [OfflineRegions] in this same style, so with no
 * connection it is read from the phone. The line is part of the map style, not a download.
 */
@Composable
fun TrailMap(trail: TrailData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val points = remember(trail.id) { trail.geoPoints.map { Point.fromLngLat(it.longitude, it.latitude) } }
    val hasLocation = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            MapView(context).apply {
                val mapboxMap = getMapboxMap()
                mapboxMap.loadStyleUri(OfflineRegions.STYLE) { style ->
                    getPlugin<AttributionPlugin>(Plugin.MAPBOX_ATTRIBUTION_PLUGIN_ID)?.updateSettings { enabled = false }
                    getPlugin<com.mapbox.maps.plugin.logo.LogoPlugin>(Plugin.MAPBOX_LOGO_PLUGIN_ID)?.updateSettings { enabled = false }
                    // A small picture of the trail does not need a scale bar across its corner.
                    getPlugin<com.mapbox.maps.plugin.scalebar.ScaleBarPlugin>(Plugin.MAPBOX_SCALEBAR_PLUGIN_ID)?.updateSettings { enabled = false }
                    gestures.updateSettings {
                        scrollEnabled = false
                        rotateEnabled = false
                        pitchEnabled = false
                        pinchToZoomEnabled = false
                        doubleTapToZoomInEnabled = false
                        quickZoomEnabled = false
                    }
                    location.updateSettings {
                        enabled = hasLocation
                        pulsingEnabled = true
                    }
                    if (points.size >= 2) {
                        mapboxMap.setCamera(
                            mapboxMap.cameraForCoordinateBounds(calculateBounds(points), EdgeInsets(72.0, 72.0, 72.0, 72.0), 0.0, 0.0)
                        )
                        TrailLayers.add(style, points, trail.difficulty)
                    }
                }
            }
        }
    )
}
