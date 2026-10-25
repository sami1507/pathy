package com.example.pathy.screens

import com.example.pathy.SignedOutNotice
import com.example.pathy.data.genderLabel
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pathy.ActivityHistoryActivity
import com.example.pathy.BookmarksActivity
import com.example.pathy.CloudSavedTrailsActivity
import com.example.pathy.EmergencySOSActivity
import com.example.pathy.EventDetailsActivity
import com.example.pathy.TrackingActivity
import com.example.pathy.NoTrailsDialog
import com.example.pathy.R
import com.example.pathy.SavedTrailsActivity
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.*
import com.example.pathy.data.ProfileRepository.getCloudProfileImageUri
import com.example.pathy.hasOfflineTrails
import com.example.pathy.isOnline
import com.example.pathy.ui.screens.GradientOutlineButton
import com.example.pathy.ui.theme.PathyColors
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    profile: ProfileInfo,
    isCurrentUser: Boolean,
    viewedUserId: String?,
    onEditProfileClick: () -> Unit,
    authViewModel: AuthViewModel,
    comViewModel: CommunityViewModel
) {
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid
    if (userId == null) {
        SignedOutNotice()
        return
    }
    val targetUserId = viewedUserId ?: userId
    val context = LocalContext.current
    val combinedItems by comViewModel.combinedItems.collectAsState()
    val store = remember { ProfileDataStore(context) }
    val coroutineScope = rememberCoroutineScope()
    var showImageDialog by remember { mutableStateOf(false) }
    var showNoTrailsDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentProfile by remember { mutableStateOf(profile) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf(profile.imageUri) }
    val cloudUri = remember { mutableStateOf<Uri?>(null) }
    val chosenImageUri = remember { if (isOnline(context)) cloudUri.value ?: imageUri else imageUri }
    var userPoints by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(targetUserId) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(targetUserId)
            .get()
            .addOnSuccessListener { document ->
                userPoints = document.getLong("reportPoints")?.toInt() ?: 0
            }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImageUri = it
            showImageDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (viewedUserId != null) {
            cloudUri.value = getCloudProfileImageUri(viewedUserId)
        }
    }

    ImageReplaceDialog(
        showDialog = showImageDialog,
        onDismiss = {
            showImageDialog = false
            pendingImageUri = null
        },
        onConfirm = {
            showImageDialog = false
            pendingImageUri?.let { uri ->
                val localUri = copyUriToInternalStorage(context, uri)

                coroutineScope.launch {
                    isUploadingImage = true
                    try {
                        val downloadUrl = StorageRepository.uploadProfileImageToStorage(localUri)

                        val updatedProfile = currentProfile.copy(
                            imageUri = Uri.parse(downloadUrl),
                            createdAt = currentProfile.createdAt
                        )
                        ProfileRepository.UpdateProfileToFirestore(
                            userId = userId,
                            profile = updatedProfile,
                            onSuccess = {
                                isUploadingImage = false
                                pendingImageUri = null
                                db.collection("posts")
                                    .whereEqualTo("userId", userId)
                                    .get()
                                    .addOnSuccessListener { result ->
                                        for (doc in result.documents) {
                                            doc.reference.update("userProfileImageUrl", downloadUrl)
                                        }
                                    }
                            },
                            onFailure = {}
                        )
                        store.saveImageUri(localUri)

                        imageUri = localUri
                        currentProfile = updatedProfile.copy(imageUri = localUri)

                    } catch (e: Exception) {
                        android.util.Log.w("PATHY", "Operation failed", e)
                    } finally {}
                }
            }
        }
    )
    val defaultImageRes = when (currentProfile.gender) {
        "female" -> R.drawable.default_pic_empty_female
        else -> R.drawable.default_pic_empty_male
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                        text = stringResource(R.string.home_profile),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (isCurrentUser) {
                        IconButton(
                            onClick = onEditProfileClick,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.profile_edit_desc),
                                tint = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = false),
            onRefresh = {
                comViewModel.listenToPosts()
                comViewModel.listenToEvents()
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                item {
                    if (isCurrentUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.End)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline)
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(chosenImageUri)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    placeholder = painterResource(id = defaultImageRes),
                                    error = painterResource(id = defaultImageRes),
                                    contentDescription = stringResource(R.string.profile_image_desc),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                )

                                if (isUploadingImage) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PathyColors.Scrim),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = PathyColors.Primary)
                                    }
                                }

                                IconButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = (-8).dp, y = (-8).dp)
                                        .background(PathyColors.Primary, shape = CircleShape)
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = stringResource(R.string.profile_change_photo),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                GradientOutlineButton(stringResource(R.string.profile_saved_trails_button)) {
                                    coroutineScope.launch {
                                        db.collection("users")
                                            .document(userId)
                                            .collection("trails_user")
                                            .get()
                                            .addOnSuccessListener { result ->
                                                if (!result.isEmpty) {
                                                    context.startActivity(Intent(context, CloudSavedTrailsActivity::class.java))
                                                } else {
                                                    showNoTrailsDialog = true
                                                }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.profile_load_failed_retry),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                }
                                GradientOutlineButton(stringResource(R.string.profile_offline_trails_button)) {
                                    if (hasOfflineTrails(context)) {
                                        context.startActivity(Intent(context, SavedTrailsActivity::class.java))
                                    } else {
                                        showNoTrailsDialog = true
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(chosenImageUri)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                placeholder = painterResource(id = defaultImageRes),
                                error = painterResource(id = defaultImageRes),
                                contentDescription = stringResource(R.string.profile_image_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(100.dp).clip(CircleShape)
                            )
                        }
                    }
                }

                item {
                    if (userPoints != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            colors = CardDefaults.cardColors(containerColor = PathyColors.Primary.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isCurrentUser) stringResource(R.string.profile_my_points) else stringResource(R.string.profile_user_points),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PathyColors.Primary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$userPoints",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(" 🏅", fontSize = MaterialTheme.typography.titleLarge.fontSize)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Entry points for the features that live in their own screens
                if (isCurrentUser) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GradientOutlineButton(stringResource(R.string.profile_record_activity_button)) {
                                    context.startActivity(Intent(context, TrackingActivity::class.java))
                                }
                                GradientOutlineButton(stringResource(R.string.profile_activity_history_button)) {
                                    context.startActivity(Intent(context, ActivityHistoryActivity::class.java))
                                }
                                GradientOutlineButton(stringResource(R.string.profile_bookmarks_button)) {
                                    context.startActivity(Intent(context, BookmarksActivity::class.java))
                                }
                                GradientOutlineButton(stringResource(R.string.profile_sos_button)) {
                                    context.startActivity(Intent(context, EmergencySOSActivity::class.java))
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.profile_user_details),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PathyColors.Primary
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            UserInfoRow("🧑", currentProfile.name)
                            UserInfoRow("📧", currentProfile.email)
                            if(currentProfile.phone!="") {
                                UserInfoRow("📱", currentProfile.phone)
                            }
                            UserInfoRow("⚥", genderLabel(currentProfile.gender))
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                val userItems = combinedItems.filter {
                    when (it) {
                        is CommunityItem.PostItem -> it.post.userId == viewedUserId
                        is CommunityItem.EventItem -> it.event.createdByUid == viewedUserId
                    }
                }

                items(userItems) { item ->
                    when (item) {
                        is CommunityItem.PostItem -> PostCard(item.post, authViewModel, comViewModel) { post ->
                            val id = post.trailId
                            if (id.isNotEmpty()) {
                                db.collection("trails")
                                    .document(id)
                                    .get()
                                    .addOnSuccessListener { document ->
                                        if (document.exists()) {
                                            val savedTrail = document.toObject(SavedTrail::class.java)
                                            if (savedTrail != null) {
                                                val trailData = savedTrail.toTrailData()
                                                val intent = Intent(context, TrailDetailsActivity::class.java).apply {
                                                    putExtra("trail", Gson().toJson(trailData))
                                                }
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.trail_load_error), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.trail_not_found), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, context.getString(R.string.trail_load_failed), Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                        is CommunityItem.EventItem -> EventCard(item.event,
                            authViewModel,
                            comViewModel,
                            onMoreClick = { event ->
                                val intent = Intent(context, EventDetailsActivity::class.java).apply {
                                    putExtra("eventId", event.id)
                                }
                                context.startActivity(intent)
                            })
                    }
                }
            }
        }
        NoTrailsDialog(
            show = showNoTrailsDialog,
            onDismiss = { showNoTrailsDialog = false }
        )
    }
}

@Composable
fun UserInfoRow(icon: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Text(icon, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
        )
    }
}
