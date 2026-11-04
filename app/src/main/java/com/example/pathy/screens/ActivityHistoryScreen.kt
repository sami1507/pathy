package com.example.pathy.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.history.ActivityHistoryManager
import com.example.pathy.history.ActivitySummary
import com.example.pathy.history.LocalActivities
import com.example.pathy.i18n.activityFilterLabel
import com.example.pathy.i18n.activityTypeLabel
import com.example.pathy.tracking.RecordingRepository
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NatureEmptyState
import com.example.pathy.ui.design.NatureLoadingState
import com.example.pathy.ui.design.NatureStatTile
import com.example.pathy.ui.design.NatureTag
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal sealed class HistoryUiState {
    object Loading : HistoryUiState()
    object Empty : HistoryUiState()
    object Error : HistoryUiState()
    data class Content(val activities: List<ActivitySummary>) : HistoryUiState()
}

private val historyFilters = listOf("All", "Hiking", "Running", "Cycling", "MTB")

/**
 * The user's recorded outings: cloud history plus anything still waiting on the phone, filtered by
 * activity type. Loads the data, then hands it to [HistoryContent] to draw.
 */
@Composable
fun ActivityHistoryScreen(
    onActivityClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onRecord: () -> Unit = {},
    /** Change this to reload the list (after something was deleted). */
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var uiState by remember { mutableStateOf<HistoryUiState>(HistoryUiState.Loading) }

    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(retry, refreshKey) {
        // Keep showing the old list while it reloads, so coming back from one outing never flashes a spinner.
        if (uiState !is HistoryUiState.Content) uiState = HistoryUiState.Loading
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

    HistoryContent(
        state = uiState,
        selectedFilter = selectedFilter,
        onFilterSelected = { selectedFilter = it },
        onActivityClick = onActivityClick,
        onBack = onBack,
        onRecord = onRecord,
        onRetry = { retry++ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onActivityClick: (String) -> Unit,
    onBack: () -> Unit,
    onRecord: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.home_history)) },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (state) {
            HistoryUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
                NatureLoadingState(stringResource(R.string.history_loading))
            }
            HistoryUiState.Error -> Box(modifier, contentAlignment = Alignment.Center) {
                NatureEmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.load_failed),
                    message = stringResource(R.string.error_network_check),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    isError = true
                )
            }
            HistoryUiState.Empty -> Box(modifier, contentAlignment = Alignment.Center) {
                NatureEmptyState(
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty_body),
                    actionLabel = stringResource(R.string.profile_record_activity_button),
                    onAction = onRecord
                )
            }
            is HistoryUiState.Content -> HistoryList(
                activities = state.activities,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
                onActivityClick = onActivityClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun HistoryList(
    activities: List<ActivitySummary>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onActivityClick: (String) -> Unit,
    modifier: Modifier
) {
    val shown = remember(activities, selectedFilter) {
        if (selectedFilter == "All") {
            activities
        } else {
            val target = if (selectedFilter.uppercase() == "MTB") "MOUNTAIN_BIKING" else selectedFilter.uppercase()
            activities.filter { it.activityType.uppercase() == target }
        }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FilterChips(
                filters = historyFilters,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected
            )
        }
        if (shown.isEmpty()) {
            item {
                NatureEmptyState(
                    icon = Icons.Filled.FilterList,
                    title = stringResource(R.string.history_empty_filter),
                    message = stringResource(R.string.history_try_other_filter),
                    actionLabel = activityFilterLabel("All"),
                    onAction = { onFilterSelected("All") }
                )
            }
        } else {
            item { HistoryTotals(shown) }
            items(shown, key = { it.id }) { activity ->
                ActivityHistoryCard(activity = activity, onClick = { onActivityClick(activity.id) })
            }
        }
    }
}

/** Count, distance and time of what is listed, so the list answers "how much have I done?" at a glance. */
@Composable
private fun HistoryTotals(activities: List<ActivitySummary>) {
    val km = activities.sumOf { it.distance.toDouble() } / 1000.0
    val millis = activities.sumOf { it.duration }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NatureStatTile(
            icon = Icons.Filled.Hiking,
            value = NumberFormat.getIntegerInstance().format(activities.size),
            label = stringResource(R.string.history_stat_activities),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        NatureStatTile(
            icon = Icons.Filled.Straighten,
            value = stringResource(R.string.unit_km, "%.1f".format(km)),
            label = stringResource(R.string.stat_distance),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        NatureStatTile(
            icon = Icons.Filled.Schedule,
            value = formatDuration(millis),
            label = stringResource(R.string.stat_duration),
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
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
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                leadingIcon = if (filter == selectedFilter) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityHistoryCard(
    activity: ActivitySummary,
    onClick: () -> Unit
) {
    val tint = getActivityColor(activity.activityType)
    NatureCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getActivityIcon(activity.activityType),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = activityTypeLabel(activity.activityType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatActivityDate(activity.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activity.pendingUpload) {
                    NatureTag(
                        label = stringResource(R.string.activity_pending_upload),
                        color = PathyColors.Warning,
                        icon = Icons.Filled.CloudUpload
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StatChip(
                        icon = Icons.Filled.Straighten,
                        value = stringResource(R.string.unit_km, (activity.distance / 1000).format(2))
                    )
                    StatChip(icon = Icons.Filled.Schedule, value = formatDuration(activity.duration))
                    StatChip(
                        icon = Icons.Filled.Terrain,
                        value = stringResource(R.string.unit_m, activity.elevationGain.toInt())
                    )
                }
            }

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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Shown over the list while one outing loads, or when it could not be loaded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailsPlaceholder(failed: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_history)) },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (failed) {
                NatureEmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.load_failed),
                    message = stringResource(R.string.error_network_check),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    isError = true
                )
            } else {
                NatureLoadingState(stringResource(R.string.history_loading))
            }
        }
    }
}

// Utility functions

private fun getActivityIcon(type: String): ImageVector {
    return when (type.uppercase()) {
        "RUNNING" -> Icons.AutoMirrored.Filled.DirectionsRun
        "CYCLING", "MOUNTAIN_BIKING", "MTB" -> Icons.AutoMirrored.Filled.DirectionsBike
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
