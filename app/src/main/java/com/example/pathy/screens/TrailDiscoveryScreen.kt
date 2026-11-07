package com.example.pathy.screens

import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.example.pathy.core.osm.Place
import com.example.pathy.data.osm.PlaceSearch
import com.example.pathy.i18n.AppLocale
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.*
import com.example.pathy.distanceTo
import com.example.pathy.offline.OfflineTrails
import com.example.pathy.offline.rememberIsOnline
import com.example.pathy.ui.design.NatureEmptyState
import com.example.pathy.ui.design.NatureOfflineBanner
import com.example.pathy.ui.theme.PathyColors
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 🏆 PREMIUM TRAIL DISCOVERY SCREEN
 * Better than AllTrails! Features:
 * - Beautiful trail cards with photos
 * - Advanced filtering (type, difficulty, length, elevation)
 * - Live trail conditions
 * - Weather integration
 * - Offline download capability
 * - Community reviews & photos
 */
@Composable
fun TrailDiscoveryScreen(
    userLocation: Point? = null,
    locationFailed: Boolean = false,
    onRetryLocation: () -> Unit = {},
    onNavigateToTrail: (EnhancedTrailData) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = remember { AppLocale.current(context) }

    // What is shown
    var trails by remember { mutableStateOf<List<EnhancedTrailData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var hiddenClosed by remember { mutableStateOf(0) }
    var searchedKm by remember { mutableStateOf(0.0) }
    var reload by remember { mutableStateOf(0) }

    // Where to look: near the phone, or any place in the world the person searched for
    var chosenPlace by remember { mutableStateOf<Place?>(null) }
    var query by remember { mutableStateOf("") }
    var placeResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var isSearchingPlace by remember { mutableStateOf(false) }
    var placeNotFound by remember { mutableStateOf(false) }
    val exploreCenter: Point? = chosenPlace?.let { Point.fromLngLat(it.position.lon, it.position.lat) } ?: userLocation

    // Walking trails first: they are what most people come for, and one kind is a much lighter request.
    // Other kinds are one tap away.
    var selectedTypes by remember { mutableStateOf(setOf(TrailAPIService.TYPE_HIKING)) }
    var selectedDifficulty by remember { mutableStateOf<DifficultyLevel?>(null) }
    var maxDistance by remember { mutableStateOf(8.0) } // km, where the search starts; it widens by itself when empty
    var slowLoad by remember { mutableStateOf(false) }
    var minElevation by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(SortOption.RECOMMENDED) }

    // Trails kept on the phone: shown with a badge, and offered in place of the list when there is no connection.
    val online by rememberIsOnline()
    var saved by remember { mutableStateOf<List<OfflineTrails.Saved>>(emptyList()) }
    LaunchedEffect(Unit) { saved = withContext(Dispatchers.IO) { OfflineTrails.list(OfflineTrails.root(context)) } }
    val savedIds = remember(saved) { saved.map { it.trail.id }.toSet() }

    fun runPlaceSearch() {
        if (query.trim().length < 2) return
        scope.launch {
            isSearchingPlace = true
            placeNotFound = false
            placeResults = PlaceSearch.search(query, language)
            placeNotFound = placeResults.isEmpty()
            isSearchingPlace = false
        }
    }

    // Load trails. LaunchedEffect cancels the previous request when a filter changes; the short delay
    // waits until a slider stops moving before asking.
    LaunchedEffect(exploreCenter?.latitude(), exploreCenter?.longitude(), selectedTypes, maxDistance, reload, online) {
        val center = exploreCenter ?: return@LaunchedEffect
        delay(300L)
        isLoading = true
        loadFailed = false
        slowLoad = false
        val slowTimer = scope.launch { delay(9_000); slowLoad = true }
        try {
            val found = TrailAPIService().discover(center, maxDistance, selectedTypes.toList())
            trails = found.trails
            hiddenClosed = found.hiddenClosed
            searchedKm = found.radiusKm
            loadFailed = found.failed && found.trails.isEmpty()
        } finally {
            slowTimer.cancel()
            isLoading = false
        }
    }

    // Filter and sort
    val displayTrails = remember(trails, selectedDifficulty, minElevation, sortBy) {
        var filtered = trails
        selectedDifficulty?.let { diff -> filtered = filtered.filter { it.difficulty == diff } }
        if (minElevation > 0) filtered = filtered.filter { (it.elevationGain ?: 0) >= minElevation }
        when (sortBy) {
            SortOption.DISTANCE        -> filtered.sortedBy { it.distanceFromUser }
            SortOption.LENGTH_DESC     -> filtered.sortedByDescending { it.distanceInMeters }
            SortOption.LENGTH_ASC      -> filtered.sortedBy { it.distanceInMeters }
            SortOption.DIFFICULTY_DESC -> filtered.sortedByDescending { it.difficulty.ordinal }
            SortOption.DIFFICULTY_ASC  -> filtered.sortedBy { it.difficulty.ordinal }
            SortOption.RECOMMENDED     -> filtered.sortedWith(compareByDescending<EnhancedTrailData> { it.info?.quality ?: 0 }.thenBy { it.distanceFromUser })
            SortOption.NAME_ASC        -> filtered.sortedBy { it.name }
        }
    }

    // The header counts what the list below shows. With no search possible that is the saved trails.
    val showingSaved = !isLoading && exploreCenter != null && saved.isNotEmpty() &&
        (loadFailed || (displayTrails.isEmpty() && !online))

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DiscoveryHeader(
                trailCount = if (showingSaved) saved.size else displayTrails.size,
                query = query,
                onQueryChange = { query = it; placeNotFound = false },
                onSearch = ::runPlaceSearch,
                isSearching = isSearchingPlace,
                exploringName = chosenPlace?.name,
                onNearMe = { chosenPlace = null; placeResults = emptyList(); query = "" },
                onFilterClick = { showFilters = !showFilters }
            )

            // Results of the place search, in the way
            if (placeResults.isNotEmpty() || placeNotFound) {
                PlaceResults(
                    places = placeResults,
                    notFound = placeNotFound,
                    onPick = { place ->
                        chosenPlace = place
                        placeResults = emptyList()
                        query = ""
                        trails = emptyList()
                    },
                    onClose = { placeResults = emptyList(); placeNotFound = false }
                )
            }

            QuickTypeFilters(
                selectedTypes = selectedTypes,
                onTypeToggle = { type ->
                    selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                }
            )

            if (showFilters) {
                AdvancedFilters(
                    selectedDifficulty = selectedDifficulty,
                    onDifficultyChange = { selectedDifficulty = it },
                    maxDistance = maxDistance,
                    onDistanceChange = { maxDistance = it },
                    minElevation = minElevation,
                    onElevationChange = { minElevation = it },
                    sortBy = sortBy,
                    onSortChange = { sortBy = it }
                )
            }

            when {
                isLoading -> LoadingTrailCards(slow = slowLoad)
                exploreCenter == null -> WaitingForLocationState(failed = locationFailed, onRetry = onRetryLocation)
                // No result and no way to ask for one: show what is saved on the phone instead of an error.
                loadFailed || (displayTrails.isEmpty() && !online) -> when {
                    saved.isNotEmpty() -> SavedFallback(saved, exploreCenter, online) { trail ->
                        context.startActivity(TrailDetailsActivity.createIntent(context, trail))
                    }
                    !online -> NatureEmptyState(
                        icon = Icons.Filled.CloudOff,
                        title = stringResource(R.string.offline_banner_title),
                        message = stringResource(R.string.offline_banner_none),
                        actionLabel = stringResource(R.string.retry),
                        onAction = { reload++ }
                    )
                    else -> LoadFailedState(onRetry = { reload++ })
                }
                displayTrails.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!online) item(key = "offline") { NatureOfflineBanner(stringResource(R.string.offline_banner_saved)) }
                    val widened = if (searchedKm > maxDistance + 0.5) context.getString(R.string.discover_widened, searchedKm.roundToInt().toString()) else null
                    val hidden = if (hiddenClosed > 0) context.getString(R.string.discover_hidden_closed, hiddenClosed.toString()) else null
                    val notes = listOfNotNull(widened, hidden)
                    if (notes.isNotEmpty()) item { InfoNote(notes.joinToString("\n")) }
                    items(displayTrails, key = { it.id }) { trail ->
                        PremiumTrailCard(
                            trail = trail,
                            offline = trail.id in savedIds,
                            onClick = { context.startActivity(TrailDetailsActivity.createIntent(context, trail.toTrailData())) }
                        )
                    }
                }
            }
        }
    }
}

/** The trails saved on the phone, nearest first, shown when a search cannot be made. */
@Composable
private fun SavedFallback(saved: List<OfflineTrails.Saved>, from: Point?, online: Boolean, onOpen: (TrailData) -> Unit) {
    val ordered = remember(saved, from) {
        saved.map { s ->
            val mid = s.trail.midpoint ?: s.trail.geoPoints.getOrNull(s.trail.geoPoints.size / 2)
            val away = if (from != null && mid != null) Point.fromLngLat(mid.longitude, mid.latitude).distanceTo(from) else null
            s to away
        }.sortedBy { it.second ?: Double.MAX_VALUE }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "note") {
            if (!online) NatureOfflineBanner(stringResource(R.string.offline_banner_saved))
            else InfoNote(stringResource(R.string.discover_load_failed))
        }
        items(ordered, key = { it.first.trail.id }) { (item, away) ->
            TrailListCard(item.trail.toCardModel(away), onClick = { onOpen(item.trail) }, offline = true)
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = PathyColors.Primary.copy(alpha = 0.10f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = PathyColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LoadFailedState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.discover_load_failed), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun PlaceResults(places: List<Place>, notFound: Boolean, onPick: (Place) -> Unit, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            if (notFound) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.discover_no_place), Modifier.weight(1f), fontSize = 14.sp)
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.common_close)) }
                }
            }
            places.forEach { place ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onPick(place) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = PathyColors.Primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            place.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryHeader(
    trailCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    exploringName: String?,
    onNearMe: () -> Unit,
    onFilterClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = PathyColors.Primary, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.discover_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = exploringName?.let { stringResource(R.string.discover_exploring, it) }
                            ?: pluralStringResource(R.plurals.trails_found_count, trailCount, trailCount),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape).size(48.dp)
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.common_filters), tint = Color.White)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.discover_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = onSearch) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.discover_search)) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                    focusedTextColor = Color(0xFF1B1B1B),
                    unfocusedTextColor = Color(0xFF1B1B1B)
                )
            )

            if (exploringName != null) {
                AssistChip(
                    onClick = onNearMe,
                    label = { Text(stringResource(R.string.discover_near_me)) },
                    leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null, Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White, labelColor = PathyColors.PrimaryDark, leadingIconContentColor = PathyColors.PrimaryDark),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }
}

@Composable
fun QuickTypeFilters(
    selectedTypes: Set<String>,
    onTypeToggle: (String) -> Unit
) {
    val types = listOf(
        TrailAPIService.TYPE_HIKING to Pair(stringResource(R.string.type_hiking_chip), PathyColors.Primary),
        TrailAPIService.TYPE_MTB to Pair("🚵 MTB", PathyColors.Accent),
        TrailAPIService.TYPE_CYCLING to Pair(stringResource(R.string.type_cycling_chip), PathyColors.Secondary),
        TrailAPIService.TYPE_OFFROAD to Pair(stringResource(R.string.type_offroad_chip), PathyColors.Warning)
    )
    
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(types) { (type, nameColor) ->
            val (name, color) = nameColor
            val isSelected = type in selectedTypes
            
            FilterChip(
                selected = isSelected,
                onClick = { onTypeToggle(type) },
                label = {
                    Text(
                        text = name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color,
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = color
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = color,
                    selectedBorderColor = color,
                    borderWidth = 2.dp
                ),
                modifier = Modifier.height(42.dp)
            )
        }
    }
}

@Composable
fun AdvancedFilters(
    selectedDifficulty: DifficultyLevel?,
    onDifficultyChange: (DifficultyLevel?) -> Unit,
    maxDistance: Double,
    onDistanceChange: (Double) -> Unit,
    minElevation: Int,
    onElevationChange: (Int) -> Unit,
    sortBy: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Difficulty
            Text(stringResource(R.string.field_difficulty), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedDifficulty == null,
                        onClick = { onDifficultyChange(null) },
                        label = { Text(stringResource(R.string.filter_all)) }
                    )
                }
                items(DifficultyLevel.values()) { diff ->
                    FilterChip(
                        selected = selectedDifficulty == diff,
                        onClick = { onDifficultyChange(diff) },
                        label = { Text(diff.name) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Distance
            Text(stringResource(R.string.max_distance, maxDistance.toInt()), fontWeight = FontWeight.Bold)
            Slider(
                value = maxDistance.toFloat(),
                onValueChange = { onDistanceChange(it.toDouble()) },
                valueRange = 5f..100f,
                steps = 18
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Elevation
            Text(stringResource(R.string.min_elevation_gain, minElevation), fontWeight = FontWeight.Bold)
            Slider(
                value = minElevation.toFloat(),
                onValueChange = { onElevationChange(it.toInt()) },
                valueRange = 0f..1000f,
                steps = 19
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sort
            Text(stringResource(R.string.sort_by), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SortOption.values()) { option ->
                    FilterChip(
                        selected = sortBy == option,
                        onClick = { onSortChange(option) },
                        label = { Text(stringResource(option.labelRes)) }
                    )
                }
            }
        }
    }
}

@Composable
fun WaitingForLocationState(failed: Boolean = false, onRetry: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (failed) {
            Icon(Icons.Filled.LocationOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.discover_location_failed),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
        } else {
            CircularProgressIndicator(color = PathyColors.Primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.location_getting), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.location_wait_or_check),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun TrailStat(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun DifficultyBadge(difficulty: DifficultyLevel) {
    val (color, text) = when (difficulty) {
        DifficultyLevel.EASY -> PathyColors.Primary to stringResource(R.string.difficulty_easy)
        DifficultyLevel.MEDIUM -> PathyColors.Warning to stringResource(R.string.difficulty_moderate)
        DifficultyLevel.HARD -> PathyColors.Accent to stringResource(R.string.difficulty_hard)
        DifficultyLevel.EXTREME -> PathyColors.DifficultyExtreme to stringResource(R.string.difficulty_extreme)
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun LoadingTrailCards(slow: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.discovering_trails), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (slow) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.discover_slow),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ExploreOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.trails_none_found), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.adjust_filters), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

