package com.example.pathy.screens

import com.example.pathy.i18n.tagLabel
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.MapResultActivity
import com.example.pathy.data.NearbyTrailsHolder
import com.example.pathy.R
import com.example.pathy.data.TrailData
import com.example.pathy.core.geo.LatLon
import com.example.pathy.data.osm.TrailRepository
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.LocationViewModel
import com.example.pathy.data.RecentSearchRepository
import com.example.pathy.data.SortOption
import com.example.pathy.data.TrailCard
import com.example.pathy.data.TrailKind
import com.example.pathy.data.TrailTags
import com.example.pathy.distanceTo
import com.example.pathy.fetchFirestoreTrailsNearby
import com.example.pathy.isLocationEnabled
import com.example.pathy.mergeTrailsUnique
import com.example.pathy.requestLocationPrompt
import com.example.pathy.ui.theme.PathyColors
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationViewModel: LocationViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val db = remember { DatabaseProvider.getDatabase(context) }
    var selectedSortOption by remember { mutableStateOf(SortOption.LENGTH_DESC) }
    val repository = remember { RecentSearchRepository(db.recentSearchDao()) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var query by remember { mutableStateOf("") }
    val visibleQuery by remember { derivedStateOf { if (isFocused) query else "" } }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(listOf<Pair<String, Point>>()) }
    var recentSearches by remember { mutableStateOf(listOf<Pair<String, Point>>()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchedTrails by remember { mutableStateOf<List<TrailData>>(emptyList()) }
    val nearTrails by NearbyTrailsHolder.trails.collectAsState()
    var externalTrails by remember { mutableStateOf<List<TrailData>>(emptyList()) }
    var combinedTrails = mergeTrailsUnique(
        baseList = if (searchedTrails.isNotEmpty()) searchedTrails else nearTrails,
        extraList = externalTrails
    )


    var selectedTags by remember { mutableStateOf(TrailTags()) }
    var isFilterDialogOpen by remember { mutableStateOf(false) }
    val sorted by remember(selectedTags, selectedSortOption, searchedTrails, nearTrails) {
        derivedStateOf {
            val trailsToShow = combinedTrails
            val filtered = filterTrails(trailsToShow, selectedTags)
            sortTrails(filtered, selectedSortOption)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val isEnabled = isLocationEnabled(context)
        if (isEnabled) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val point = Point.fromLngLat(location.longitude, location.latitude)
                        locationViewModel.updateLocation(point)
                        coroutineScope.launch {
                            val trailsFromApi = fetchTrails(point)
                            val trailsFromFirestore = fetchFirestoreTrailsNearby(point)
                            val allNearby = (trailsFromApi + trailsFromFirestore).distinctBy { it.id }
                            NearbyTrailsHolder.update(allNearby)
                            searchedTrails = emptyList()
                            results = emptyList()
                            query = ""
                            focusManager.clearFocus()
                        }
                    }
                }
        }
    }
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) {
            repository.getAllSearches()
        }
        recentSearches = saved.map { it.name to Point.fromLngLat(it.longitude, it.latitude) }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val point = Point.fromLngLat(it.longitude, it.latitude)
                        locationViewModel.updateLocation(point)
                        coroutineScope.launch {
                            externalTrails = fetchFirestoreTrailsNearby(point)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e("Location", "אין הרשאה למיקום: ${e.message}", e)
            }
        }
    }

    LaunchedEffect(query) {
        if (query.length >= 2) {
            performSearch(context, query) { res ->
                results = res
            }
        } else {
            results = emptyList()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = PathyColors.AppBar,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.common_search),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = visibleQuery,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .shadow(4.dp, shape = RoundedCornerShape(32.dp)),
                    placeholder = { Text(stringResource(R.string.search_place_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (isFocused && query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(32.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = PathyColors.Primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f)
                    ),
                    interactionSource = interactionSource
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isFocused) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            coroutineScope.launch {
                                if (query.length >= 2) {
                                    isLoading = true
                                    try {
                                        performSearch(context, query) { foundResults ->
                                            coroutineScope.launch {
                                                try {
                                                    if (foundResults.isNotEmpty()) {
                                                        val point = foundResults.first().second
                                                        val trailsFromApi = fetchTrails(point)
                                                        val trailsFromFirestore = fetchFirestoreTrailsNearby(point)
                                                        val combined = (trailsFromApi + trailsFromFirestore).distinctBy { it.id }

                                                        val intent = Intent(context, MapResultActivity::class.java).apply {
                                                            putExtra("lat", point.latitude())
                                                            putExtra("lng", point.longitude())
                                                            putExtra("trailsJson", TrailData.toJson(combined))
                                                        }
                                                        context.startActivity(intent)

                                                        query = ""
                                                    } else {
                                                        isLoading = false
                                                        Toast.makeText(context, context.getString(R.string.search_no_results), Toast.LENGTH_SHORT).show()
                                                    }
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {
                                        isLoading = false
                                        Toast.makeText(context, context.getString(R.string.search_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
                    ) {
                        Text(stringResource(R.string.common_search))
                    }
                }
            }

                Spacer(modifier = Modifier.height(16.dp))

            if (isFocused) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                if (!isLocationEnabled(context)) {
                                    requestLocationPrompt(context, launcher)
                                    return@clickable
                                }
                                isLoading = true
                                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                    .addOnSuccessListener { location ->
                                        if (location != null) {
                                            val point = Point.fromLngLat(location.longitude, location.latitude)
                                            focusManager.clearFocus()
                                            coroutineScope.launch {
                                                try {
                                                    val trailsFromApi = fetchTrails(point)
                                                    val trailsFromFirestore = fetchFirestoreTrailsNearby(point)
                                                    val combined = (trailsFromApi + trailsFromFirestore).distinctBy { it.id }
                                                    val intent = Intent(context, MapResultActivity::class.java).apply {
                                                        putExtra("lat", point.latitude())
                                                        putExtra("lng", point.longitude())
                                                        putExtra("trailsJson", TrailData.toJson(combined))
                                                    }
                                                    context.startActivity(intent)
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                        }
                                    }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.search_show_nearby), style = MaterialTheme.typography.bodyLarge)
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = stringResource(R.string.search_show_my_location),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isFocused && query.isEmpty() && recentSearches.isNotEmpty()) {
                Text(stringResource(R.string.search_history_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp)
                        .border(
                            width = 3.dp,
                            color = PathyColors.MenuSearchColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp)
                ) {
                    LazyColumn {
                        items(recentSearches.reversed()) { (name, point) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        focusManager.clearFocus()
                                        isLoading = true
                                        coroutineScope.launch {
                                            try {
                                                val trailsFromApi = fetchTrails(point)
                                                val trailsFromFirestore = fetchFirestoreTrailsNearby(point)
                                                val allNearby = (trailsFromApi + trailsFromFirestore).distinctBy { it.id }
                                                val intent = Intent(
                                                    context,
                                                    MapResultActivity::class.java
                                                ).apply {
                                                    putExtra("lat", point.latitude())
                                                    putExtra("lng", point.longitude())
                                                    putExtra("trailsJson", TrailData.toJson(allNearby))
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "TrailFetch",
                                                    "שגיאה בטעינת מסלולים: ${e.message}"
                                                )
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = name, style = MaterialTheme.typography.bodyLarge)
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.trails_loading))
                }
            } else {
                if (!isLoading && results.isEmpty() && query.length >= 2) {
                    Text(
                        text = stringResource(R.string.search_no_results_for, query),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                if (isFocused && results.isNotEmpty()) {
                    results.forEach { (name, point) ->

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            repository.insertSearchIfNotExists(
                                                name = name,
                                                latitude = point.latitude(),
                                                longitude = point.longitude()
                                            )
                                            val trailsFromApi = fetchTrails(point)
                                            val trailsFromFirestore = fetchFirestoreTrailsNearby(point)
                                            val combinedTrails = (trailsFromApi + trailsFromFirestore).distinctBy { it.id }
                                            val intent = Intent(
                                                context,
                                                MapResultActivity::class.java
                                            ).apply {
                                                putExtra("lat", point.latitude())
                                                putExtra("lng", point.longitude())
                                                putExtra("trailsJson", TrailData.toJson(combinedTrails))
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e(
                                                "TrailFetch",
                                                "שגיאה בטעינת מסלולים: ${e.message}"
                                            )
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (!isFocused && (searchedTrails.isNotEmpty() || nearTrails.isNotEmpty())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .border(1.dp, PathyColors.Primary, RoundedCornerShape(8.dp))
                        .clickable { isDropdownExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { isFilterDialogOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.MenuSearchColor)
                        ) {
                            Text(stringResource(R.string.search_filter_button))
                        }

                        Button(
                            onClick = { isDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PathyColors.MenuSearchColor)
                        ) {
                            Text(stringResource(R.string.search_sort_button, stringResource(selectedSortOption.labelRes)))
                        }
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        listOf(
                            SortOption.LENGTH_DESC,
                            SortOption.LENGTH_ASC,
                            SortOption.DIFFICULTY_ASC,
                            SortOption.DIFFICULTY_DESC,
                            SortOption.NAME_ASC
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    selectedSortOption = option
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (!isFocused) {
                if (searchedTrails.isNotEmpty()) {
                    Text(stringResource(R.string.search_results_title), style = MaterialTheme.typography.titleMedium)
                } else if (nearTrails.isNotEmpty()) {
                    Text(stringResource(R.string.search_nearby_title), style = MaterialTheme.typography.titleMedium)
                }
                LazyColumn {
                    items(sorted) { trail ->
                        TrailCard(trail, onClick = {
                            val intent = TrailDetailsActivity.createIntent(context, trail)
                            context.startActivity(intent)
                        })
                    }
                }
            }
        }
    }
    if (isFilterDialogOpen) {
        AlertDialog(
            onDismissRequest = { isFilterDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = { isFilterDialogOpen = false }
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
            },
            title = {
                Text(stringResource(R.string.filter_by_tags_title), style = MaterialTheme.typography.titleLarge)
            },
            text = {
                FilterByTagsSection(
                    selectedTags = selectedTags,
                    onChange = { selectedTags = it }
                )
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.background
        )
    }
}

suspend fun performSearch(
    context: Context,
    query: String,
    onResult: (List<Pair<String, Point>>) -> Unit
) {
    val token = context.getString(R.string.mapbox_access_token)
    val client = OkHttpClient()

    fun runQuery(lang: String): List<Pair<String, Point>> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = buildString {
            append("https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json")
            append("?access_token=$token")
            append("&language=$lang")
            append("&autocomplete=true")
            append("&types=country,place,locality,address")
        }

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val json = response.body?.string() ?: return emptyList()

        val jsonObject = JSONObject(json)
        val features = jsonObject.optJSONArray("features") ?: return emptyList()

        val cities = mutableListOf<Pair<String, Point>>()
        val others = mutableListOf<Pair<String, Point>>()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val name = feature.optString("place_name", "")
            val center = feature.optJSONArray("center")
            if (center != null && center.length() == 2) {
                val point = Point.fromLngLat(center.getDouble(0), center.getDouble(1))
                val type = feature.optJSONArray("place_type")?.optString(0)
                if (type == "place" || type == "locality" || type == "country") {
                    cities.add(name to point)
                } else {
                    others.add(name to point)
                }
            }
        }
        return cities + others
    }

    withContext(Dispatchers.IO) {
        try {
            var results = runQuery("he")

            if (results.isEmpty()) {
                results = runQuery("en")
            }

            withContext(Dispatchers.Main) {
                onResult(results)
            }
        } catch (e: Exception) {
            Log.e("Search", "שגיאה בחיפוש: ${e.message}", e)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterByTagsSection(
    selectedTags: TrailTags,
    onChange: (TrailTags) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text(stringResource(R.string.filter_by_tags_header), style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(stringResource(R.string.tag_forest), selectedTags.isForest) {
                onChange(selectedTags.copy(isForest = !selectedTags.isForest))
            }
            FilterChip(stringResource(R.string.tag_water), selectedTags.hasWater) {
                onChange(selectedTags.copy(hasWater = !selectedTags.hasWater))
            }
            FilterChip(stringResource(R.string.tag_viewpoint), selectedTags.hasViewpoint) {
                onChange(selectedTags.copy(hasViewpoint = !selectedTags.hasViewpoint))
            }
            FilterChip(stringResource(R.string.tag_extreme), selectedTags.hasExtremeSection) {
                onChange(selectedTags.copy(hasExtremeSection = !selectedTags.hasExtremeSection))
            }
            FilterChip(stringResource(R.string.tag_accessible), selectedTags.isAccessibility) {
                onChange(selectedTags.copy(isAccessibility = !selectedTags.isAccessibility))
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.filter_suitable_for), style = MaterialTheme.typography.bodyMedium)

        // כאן מגדירים את הקטגוריות עם שם "נקי" + אימוג'י
        val categories = listOf(
            "הליכה" to "🚶‍♂️",
            "אופניים" to "🚴‍♂️",
            "רכב" to "🚗"
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (cat, emoji) ->
                // נרמל כדי שלא יתנגש אם נשמר עם אמוג'י בעבר
                val isSelected = selectedTags.suitableFor.any { normalizeCategory(it) == cat }

                FilterChip("$emoji ${tagLabel(cat)}", isSelected) {
                    val updated = selectedTags.suitableFor.toMutableList()
                        .filterNot { normalizeCategory(it) == cat } // הסרה
                        .toMutableList()
                    if (!isSelected) updated.add(cat)             // הוספה
                    onChange(selectedTags.copy(suitableFor = updated))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.filter_group_type), style = MaterialTheme.typography.bodyMedium)
        val groupOptions = listOf("בודד", "זוג", "משפחה", "ילדים קטנים")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupOptions.forEach { kind ->
                val isSelected = kind in selectedTags.suggestedGroupKind
                FilterChip(tagLabel(kind), isSelected) {
                    val updated = if (isSelected)
                        selectedTags.suggestedGroupKind - kind
                    else
                        selectedTags.suggestedGroupKind + kind
                    onChange(selectedTags.copy(suggestedGroupKind = updated))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        var sliderValue by remember { mutableStateOf((selectedTags.elevationGain ?: 0).toFloat()) }
        Text(stringResource(R.string.filter_min_elevation, sliderValue.toInt()),
            style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onChange(selectedTags.copy(elevationGain = it.toInt()))
            },
            valueRange = 0f..1000f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onChange(TrailTags()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(stringResource(R.string.filter_reset), color = Color.White)
        }
    }
}

/** Trails within 3 km of [center]. One cached request serves every screen (see TrailRepository). */
suspend fun fetchTrails(center: Point): List<TrailData> =
    TrailRepository.nearby(LatLon(center.latitude(), center.longitude()), radiusM = 3_000)
        .map(TrailRepository::toTrailData)

fun filterTrails(trails: List<TrailData>, tags: TrailTags): List<TrailData> {
    val selectedCats = tags.suitableFor.map(::normalizeCategory).toSet()

    return trails.filter { trail ->
        val baseFilters =
            (!tags.isForest || trail.tags.isForest) &&
                    (!tags.hasWater || trail.tags.hasWater) &&
                    (!tags.hasViewpoint || trail.tags.hasViewpoint) &&
                    (!tags.hasExtremeSection || trail.tags.hasExtremeSection) &&
                    (!tags.isAccessibility || trail.tags.isAccessibility) &&
                    (tags.elevationGain == null || (trail.tags.elevationGain ?: 0) >= tags.elevationGain) &&
                    (tags.suggestedGroupKind.isEmpty() || tags.suggestedGroupKind.all { it in trail.tags.suggestedGroupKind })

        val kindMatches = if (selectedCats.isEmpty()) true else {
            val trailKindLabel = when (trail.kind()) {
                TrailKind.HIKING  -> "הליכה"
                TrailKind.BICYCLE -> "אופניים"
                TrailKind.CAR     -> "רכב"
            }
            selectedCats.contains(trailKindLabel)
        }

        baseFilters && kindMatches
    }
}

fun sortTrails(trails: List<TrailData>, option: SortOption): List<TrailData> {
    return when (option) {
        SortOption.LENGTH_DESC     -> trails.sortedByDescending { it.distanceInMeters }
        SortOption.LENGTH_ASC      -> trails.sortedBy { it.distanceInMeters }
        SortOption.DIFFICULTY_ASC  -> trails.sortedBy { it.difficulty.ordinal }
        SortOption.DIFFICULTY_DESC -> trails.sortedByDescending { it.difficulty.ordinal }
        SortOption.NAME_ASC        -> trails.sortedBy { it.name }
        else -> trails // DISTANCE and RATING require EnhancedTrailData fields not present on TrailData
    }
}

fun TrailData.kind(): TrailKind = when {
    this.isHikingTrail -> TrailKind.HIKING
    this.tags.suitableFor.any { it == "אופניים" } -> TrailKind.BICYCLE
    this.tags.suitableFor.any { it == "רכב" } -> TrailKind.CAR
    else -> TrailKind.HIKING
}

private fun normalizeCategory(label: String): String = label.trim()
