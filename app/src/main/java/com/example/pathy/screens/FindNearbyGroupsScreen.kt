package com.example.pathy.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.Indicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pathy.CreateGroupViewModel
import com.example.pathy.data.Group
import com.example.pathy.GroupSyncManager
import com.example.pathy.data.OfflineGroupQueue
import com.example.pathy.data.OfflineMembershipQueue
import com.example.pathy.data.PendingMember
import com.example.pathy.data.openNavigationWithGroup
import com.example.pathy.isOnline
import com.example.pathy.ui.theme.PathyColors
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindNearbyGroupsScreen(
    radiusMeters: Float = 5_000f,
    onBack: (() -> Unit)? = null,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val context = LocalContext.current

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var nearbyGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var localOfflineGroups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val askPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLastLocation(
                context = context,
                onLocation = { loc ->
                    currentLocation = loc
                    loadGroups(
                        viewModel = viewModel,
                        userLocation = loc,
                        radiusMeters = radiusMeters,
                        context = context,
                        onStart = { isLoading = true },
                        onDone = { nearby, localOnly ->
                            nearbyGroups = nearby
                            localOfflineGroups = localOnly
                            isLoading = false
                        },
                        onError = { isLoading = false }
                    )
                },
                onError = {
                    Toast.makeText(context, context.getString(R.string.error_location_failed), Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        GroupSyncManager.syncPendingGroups(context) {
            currentLocation?.let { loc ->
                loadGroups(
                    viewModel = viewModel,
                    userLocation = loc,
                    radiusMeters = radiusMeters,
                    context = context,
                    onStart = { isLoading = true },
                    onDone = { nearby, localOnly ->
                        nearbyGroups = nearby
                        localOfflineGroups = localOnly
                        isLoading = false
                    },
                    onError = { isLoading = false }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLastLocation(
                context = context,
                onLocation = { loc ->
                    currentLocation = loc
                    loadGroups(
                        viewModel = viewModel,
                        userLocation = loc,
                        radiusMeters = radiusMeters,
                        context = context,
                        onStart = { isLoading = true },
                        onDone = { nearby, localOnly ->
                            nearbyGroups = nearby
                            localOfflineGroups = localOnly
                            isLoading = false
                        },
                        onError = { isLoading = false }
                    )
                },
                onError = { Toast.makeText(context, context.getString(R.string.error_location_not_received), Toast.LENGTH_SHORT).show() }
            )
        } else {
            askPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val bg = Brush.verticalGradient(
        colors = listOf(
            PathyColors.Primary.copy(alpha = 0.16f),
            PathyColors.Accent.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = { Text(stringResource(R.string.groups_nearby_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !isLoading && currentLocation != null,
                            onClick = {
                                currentLocation?.let { loc ->
                                    loadGroups(
                                        viewModel = viewModel,
                                        userLocation = loc,
                                        radiusMeters = radiusMeters,
                                        context = context,
                                        onStart = { isLoading = true },
                                        onDone = { nearby, localOnly ->
                                            nearbyGroups = nearby
                                            localOfflineGroups = localOnly
                                            isLoading = false
                                        },
                                        onError = { isLoading = false }
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                AssistChip(
                    onClick = {},
                    label = {
                        val txt = when {
                            permissionDenied -> context.getString(R.string.groups_status_no_permission)
                            currentLocation == null -> context.getString(R.string.groups_status_locating)
                            else -> context.getString(R.string.groups_status_location_on)
                        }
                        Text(txt)
                    }
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicator = { positions ->
                        Indicator(
                            modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                            color = PathyColors.Accent
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.groups_tab_nearby), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.groups_tab_local), fontWeight = FontWeight.Bold) }
                    )
                }

                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    permissionDenied -> {
                        PermissionHint(onRequest = { askPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
                    }
                    currentLocation == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.groups_waiting_location))
                        }
                    }
                    else -> {
                        if (selectedTab == 0) {
                            if (nearbyGroups.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.groups_none_in_range, (radiusMeters/1000).roundToInt()))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(nearbyGroups, key = { it.id }) { group ->
                                        GroupCard(
                                            group = group,
                                            isLocalOnly = false,
                                            onJoin = {
                                                val user = FirebaseAuth.getInstance().currentUser
                                                if (user == null) {
                                                    Toast.makeText(context, context.getString(R.string.groups_sign_in_to_join), Toast.LENGTH_SHORT).show()
                                                    return@GroupCard
                                                }

                                                if (isOnline(context)) {
                                                    viewModel.joinGroup(
                                                        groupId = group.id,
                                                        userId = user.uid,
                                                        userName = user.displayName ?: context.getString(R.string.default_user_name)
                                                    ) { success ->
                                                        if (success) {
                                                            Toast.makeText(context, context.getString(R.string.groups_joined), Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, context.getString(R.string.groups_already_joined), Toast.LENGTH_SHORT).show()
                                                        }
                                                        openNavigationWithGroup(
                                                            context = context,
                                                            groupId = group.id,
                                                            fromGroups = true,
                                                            isLocalOnly = false
                                                        )
                                                    }
                                                } else {
                                                    OfflineMembershipQueue(context).enqueue(
                                                        PendingMember(
                                                            groupId = group.id,
                                                            uid = user.uid,
                                                            name = user.displayName ?: context.getString(R.string.default_user_name)
                                                        )
                                                    )
                                                    Toast.makeText(context, context.getString(R.string.groups_join_saved_offline), Toast.LENGTH_SHORT).show()
                                                    openNavigationWithGroup(
                                                        context = context,
                                                        groupId = group.id,
                                                        fromGroups = true,
                                                        isLocalOnly = true
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            val localList = localOfflineGroups
                            if (localList.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.groups_none_local))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(localList, key = { it.id }) { group ->
                                        GroupCard(
                                            group = group,
                                            isLocalOnly = true,
                                            onJoin = {
                                                FirebaseAuth.getInstance().currentUser?.let { user ->
                                                    OfflineMembershipQueue(context).enqueue(
                                                        PendingMember(
                                                            groupId = group.id,
                                                            uid = user.uid,
                                                            name = user.displayName ?: context.getString(R.string.default_user_name)
                                                        )
                                                    )
                                                }
                                                openNavigationWithGroup(
                                                    context = context,
                                                    groupId = group.id,
                                                    fromGroups = true,
                                                    isLocalOnly = true
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    isLocalOnly: Boolean,
    onJoin: () -> Unit
) {
    val container = if (isLocalOnly) PathyColors.Accent.copy(alpha = 0.10f)
    else PathyColors.Primary.copy(alpha = 0.08f)
    val titleColor = if (isLocalOnly) PathyColors.Accent else PathyColors.Primary
    val btnColors = ButtonDefaults.buttonColors(
        containerColor = if (isLocalOnly) PathyColors.Accent else PathyColors.Primary
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            if (group.description.isNotBlank()) {
                Text(group.description, style = MaterialTheme.typography.bodyMedium)
            }
            val distanceText = group.distanceFromUser?.let { d ->
                if (d >= 1000) stringResource(R.string.unit_km, "%.1f".format(d / 1000f)) else stringResource(R.string.unit_m, d.roundToInt())
            } ?: if (isLocalOnly) stringResource(R.string.groups_no_location_offline) else stringResource(R.string.groups_no_location)
            Text(stringResource(R.string.groups_distance, distanceText), style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                colors = btnColors,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isLocalOnly) stringResource(R.string.groups_connect_offline) else stringResource(R.string.groups_join), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionHint(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.groups_location_needed))
        Button(onClick = onRequest) { Text(stringResource(R.string.common_grant_permission)) }
    }
}

@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
private fun fetchLastLocation(
    context: android.content.Context,
    onLocation: (Location) -> Unit,
    onError: (Throwable) -> Unit
) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    fused.lastLocation
        .addOnSuccessListener { loc -> if (loc != null) onLocation(loc) else onError(IllegalStateException("null location")) }
        .addOnFailureListener(onError)
}

private fun loadGroups(
    viewModel: CreateGroupViewModel,
    userLocation: Location,
    radiusMeters: Float,
    context: android.content.Context,
    onStart: () -> Unit,
    onDone: (nearby: List<Group>, localOnly: List<Group>) -> Unit,
    onError: (Throwable) -> Unit
) {
    onStart()
    viewModel.fetchGroups { online ->
        try {
            val offlineAll = OfflineGroupQueue(context).loadPendingGroups()
            val offlineOnly = offlineAll.filter { off -> online.none { it.id == off.id } }

            fun withDistance(list: List<Group>): List<Group> =
                list.mapNotNull { g ->
                    val lat = g.lat
                    val lng = g.lng
                    if (lat == null || lng == null) return@mapNotNull null
                    val loc = Location("").apply { latitude = lat; longitude = lng }
                    g.distanceFromUser = userLocation.distanceTo(loc)
                    g
                }

            val nearby = withDistance(online)
                .filter { (it.distanceFromUser ?: Float.MAX_VALUE) <= radiusMeters }
                .sortedBy { it.distanceFromUser ?: Float.MAX_VALUE }
                .distinctBy { it.id }

            val localOnly = offlineOnly.onEach { g ->
                val lat = g.lat; val lng = g.lng
                g.distanceFromUser = if (lat != null && lng != null) {
                    val loc = Location("").apply { latitude = lat; longitude = lng }
                    userLocation.distanceTo(loc)
                } else null
            }.distinctBy { it.id }

            onDone(nearby, localOnly)
        } catch (t: Throwable) {
            onError(t)
        }
    }
}