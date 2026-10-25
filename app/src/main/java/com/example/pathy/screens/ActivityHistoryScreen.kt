package com.example.pathy.screens

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.pathy.i18n.activityFilterLabel
import com.example.pathy.i18n.activityTypeLabel
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.pathy.ui.design.pathyTopBarColors
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.history.ActivityHistoryManager
import com.example.pathy.history.LocalActivities
import com.example.pathy.tracking.RecordingRepository
import com.example.pathy.history.ActivitySummary
import com.google.firebase.auth.FirebaseAuth
import com.example.pathy.ui.theme.PathyColors
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private sealed class HistoryUiState {
    object Loading : HistoryUiState()
    object Empty : HistoryUiState()
    object Error : HistoryUiState()
    data class Content(val activities: List<ActivitySummary>) : HistoryUiState()
}

/**
 * 📜 ACTIVITY HISTORY SCREEN
 * 
 * Beautiful activity history with filters
 * - Timeline view
 * - Stats summaries
 * - Filter by type/date
 * - Better than Strava history!
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    onActivityClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    /** Change this to reload the list (after something was deleted). */
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Hiking", "Running", "Cycling", "MTB")
    var uiState by remember { mutableStateOf<HistoryUiState>(HistoryUiState.Loading) }

    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry, refreshKey) {
        uiState = HistoryUiState.Loading
        // Outings still on the phone (recorded offline, or not uploaded yet) are listed too, so a
        // finished walk never seems to have vanished.
        val waiting = RecordingRepository(DatabaseProvider.getDatabase(context).recordingDao())
            .pendingUploads().filter { it.distanceM > 0.0 }
            .map(LocalActivities::summary)
            .sortedByDescending { it.startTime }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val cloud = if (uid == null) Result.success(emptyList()) else ActivityHistoryManager().getActivityHistory(uid)
        val cloudList = cloud.getOrNull().orEmpty()
        val cloudIds = cloudList.map { it.id }.toSet()
        val all = waiting.filter { it.id !in cloudIds } + cloudList
        uiState = when {
            all.isNotEmpty() -> HistoryUiState.Content(all)
            cloud.isFailure -> HistoryUiState.Error
            else -> HistoryUiState.Empty
        }
    }

    val activities = (uiState as? HistoryUiState.Content)?.activities ?: emptyList()

    val filteredActivities = remember(activities, selectedFilter) {
        if (selectedFilter == "All") {
            activities
        } else {
            val targetType = when (selectedFilter.uppercase()) {
                "MTB" -> "MOUNTAIN_BIKING"
                else  -> selectedFilter.uppercase()
            }
            activities.filter { it.activityType.uppercase() == targetType }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_history),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { /* Filter */ }) {
                        Icon(Icons.Filled.FilterList, stringResource(R.string.history_filter))
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is HistoryUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PathyColors.Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.history_loading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is HistoryUiState.Error -> {
                Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(androidx.compose.ui.res.stringResource(com.example.pathy.R.string.load_failed))
                    TextButton(onClick = { retry++ }) { Text(androidx.compose.ui.res.stringResource(com.example.pathy.R.string.retry)) }
                }
            }
            is HistoryUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyHistoryState()
                }
            }
            is HistoryUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FilterChips(
                            filters = filters,
                            selectedFilter = selectedFilter,
                            onFilterSelected = { selectedFilter = it }
                        )
                    }
                    item {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.pathy.R.string.home_history),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(filteredActivities) { activity ->
                        ActivityHistoryCard(
                            activity = activity,
                            onClick = { onActivityClick(activity.id) }
                        )
                    }
                    if (filteredActivities.isEmpty()) {
                        item { EmptyFilterState(selectedFilter) }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(activityFilterLabel(filter)) },
                leadingIcon = if (filter == selectedFilter) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                } else null
            )
        }
    }
}

@Composable
fun ActivityHistoryCard(
    activity: ActivitySummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Activity icon
            Surface(
                shape = CircleShape,
                color = getActivityColor(activity.activityType).copy(alpha = 0.2f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getActivityIcon(activity.activityType),
                        contentDescription = null,
                        tint = getActivityColor(activity.activityType),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // Activity info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = activityTypeLabel(activity.activityType),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = formatActivityDate(activity.startTime),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (activity.pendingUpload) {
                    Text(
                        text = stringResource(R.string.activity_pending_upload),
                        fontSize = 12.sp,
                        color = PathyColors.Warning
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatChip(
                        icon = Icons.Filled.Straighten,
                        value = stringResource(R.string.unit_km, (activity.distance / 1000).format(2))
                    )
                    
                    StatChip(
                        icon = Icons.Filled.Schedule,
                        value = formatDuration(activity.duration)
                    )
                    
                    StatChip(
                        icon = Icons.Filled.Terrain,
                        value = stringResource(R.string.unit_m, activity.elevationGain.toInt())
                    )
                }
            }
            
            // Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatChip(icon: ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PathyColors.Primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.DirectionsRun,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.history_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = stringResource(R.string.history_empty_body),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun EmptyFilterState(filter: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.history_empty_filter),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.history_try_other_filter),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// Utility functions

private fun getActivityIcon(type: String): ImageVector {
    return when (type.uppercase()) {
        "RUNNING" -> Icons.Filled.DirectionsRun
        "CYCLING" -> Icons.Filled.DirectionsBike
        "MOUNTAIN_BIKING", "MTB" -> Icons.Filled.DirectionsBike
        else -> Icons.Filled.Hiking
    }
}

private fun getActivityColor(type: String): Color {
    return when (type.uppercase()) {
        "RUNNING" -> PathyColors.Secondary
        "CYCLING" -> PathyColors.Warning
        "MOUNTAIN_BIKING", "MTB" -> PathyColors.Berry
        else -> PathyColors.Primary
    }
}

/** Date and time in the order and month names of the app language, e.g. "20 Oct 2026, 09:41". */
private fun formatActivityDate(timestamp: Long): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "d MMM yyyy HH:mm")
    return SimpleDateFormat(pattern, locale).format(Date(timestamp))
}

@Composable
private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
