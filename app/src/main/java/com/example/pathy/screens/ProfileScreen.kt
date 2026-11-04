package com.example.pathy.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.pathy.ActivityHistoryActivity
import com.example.pathy.BookmarksActivity
import com.example.pathy.CloudSavedTrailsActivity
import com.example.pathy.EmergencySOSActivity
import com.example.pathy.EventDetailsActivity
import com.example.pathy.NoTrailsDialog
import com.example.pathy.R
import com.example.pathy.SavedTrailsActivity
import com.example.pathy.SignedOutNotice
import com.example.pathy.TrackingActivity
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.*
import com.example.pathy.data.ProfileRepository.getCloudProfileImageUri
import com.example.pathy.hasOfflineTrails
import com.example.pathy.isOnline
import com.example.pathy.ui.design.NatureActionTile
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NaturePrimaryAction
import com.example.pathy.ui.design.NatureSectionHeader
import com.example.pathy.ui.design.NatureTag
import com.example.pathy.ui.design.TopoBackground
import com.example.pathy.ui.design.MountainRange
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.theme.Forest800
import com.example.pathy.ui.theme.Moss600
import com.example.pathy.ui.theme.PathyColors
import com.example.pathy.ui.theme.Sand400
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** What the profile screen shows. Kept separate from Firebase so every state can be drawn and tested. */
internal data class ProfileUiState(
    val name: String,
    val email: String,
    val phone: String,
    val gender: String,
    val avatar: Any?,
    val points: Int?,
    val isCurrentUser: Boolean,
    val uploadingPhoto: Boolean = false,
    val hasPosts: Boolean = false,
    val refreshing: Boolean = false
)

/** What the profile screen can ask for. The screen decides how each one is carried out. */
internal class ProfileActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onChangePhoto: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onSavedTrails: () -> Unit = {},
    val onOfflineTrails: () -> Unit = {},
    val onHistory: () -> Unit = {},
    val onBookmarks: () -> Unit = {},
    val onSos: () -> Unit = {},
    val onRefresh: () -> Unit = {}
)

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
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val combinedItems by comViewModel.combinedItems.collectAsState()
    val feedLoading by comViewModel.isLoading.collectAsState()
    val store = remember { ProfileDataStore(context) }
    val coroutineScope = rememberCoroutineScope()
    var showImageDialog by remember { mutableStateOf(false) }
    var showNoTrailsDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentProfile by remember { mutableStateOf(profile) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf(profile.imageUri) }
    val cloudUri = remember { mutableStateOf<Uri?>(null) }
    val online = remember { isOnline(context) }
    val chosenImageUri = if (online) cloudUri.value ?: imageUri else imageUri
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

    LaunchedEffect(viewedUserId) {
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

    val userItems = combinedItems.filter {
        when (it) {
            is CommunityItem.PostItem -> it.post.userId == targetUserId
            is CommunityItem.EventItem -> it.event.createdByUid == targetUserId
        }
    }

    val avatarModel = remember(chosenImageUri) {
        ImageRequest.Builder(context)
            .data(chosenImageUri)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val actions = ProfileActions(
        onBack = { backDispatcher?.onBackPressed() },
        onEdit = onEditProfileClick,
        onChangePhoto = { imagePickerLauncher.launch("image/*") },
        onRecord = { context.startActivity(Intent(context, TrackingActivity::class.java)) },
        onSavedTrails = {
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
        },
        onOfflineTrails = {
            if (hasOfflineTrails(context)) {
                context.startActivity(Intent(context, SavedTrailsActivity::class.java))
            } else {
                showNoTrailsDialog = true
            }
        },
        onHistory = { context.startActivity(Intent(context, ActivityHistoryActivity::class.java)) },
        onBookmarks = { context.startActivity(Intent(context, BookmarksActivity::class.java)) },
        onSos = { context.startActivity(Intent(context, EmergencySOSActivity::class.java)) },
        onRefresh = { comViewModel.refresh() }
    )

    ProfileContent(
        state = ProfileUiState(
            name = currentProfile.name,
            email = currentProfile.email,
            phone = currentProfile.phone,
            gender = currentProfile.gender,
            avatar = avatarModel,
            points = userPoints,
            isCurrentUser = isCurrentUser,
            uploadingPhoto = isUploadingImage,
            hasPosts = userItems.isNotEmpty(),
            refreshing = feedLoading
        ),
        actions = actions
    ) {
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
                is CommunityItem.EventItem -> EventCard(
                    item.event,
                    authViewModel,
                    comViewModel,
                    onMoreClick = { event ->
                        val intent = Intent(context, EventDetailsActivity::class.java).apply {
                            putExtra("eventId", event.id)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
    NoTrailsDialog(
        show = showNoTrailsDialog,
        onDismiss = { showNoTrailsDialog = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    actions: ProfileActions,
    posts: LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_profile)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.isCurrentUser) {
                        IconButton(onClick = actions.onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_edit_desc))
                        }
                    }
                },
                colors = pathyTopBarColors()
            )
        }
    ) { padding ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = state.refreshing),
            onRefresh = actions.onRefresh,
            modifier = Modifier.padding(padding)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ProfileHero(state, actions.onChangePhoto) }

            if (state.isCurrentUser) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NaturePrimaryAction(
                            label = stringResource(R.string.profile_record_activity_button),
                            icon = Icons.Filled.FiberManualRecord,
                            iconTint = PathyColors.Error,
                            onClick = actions.onRecord
                        )
                        TileRow(
                            first = Tile(stringResource(R.string.profile_saved_trails_button), Icons.Filled.CloudDone, actions.onSavedTrails),
                            second = Tile(stringResource(R.string.profile_offline_trails_button), Icons.Filled.FolderOpen, actions.onOfflineTrails)
                        )
                        TileRow(
                            first = Tile(stringResource(R.string.profile_activity_history_button), Icons.Filled.History, actions.onHistory),
                            second = Tile(stringResource(R.string.profile_bookmarks_button), Icons.Filled.Bookmark, actions.onBookmarks)
                        )
                        EmergencyRow(actions.onSos)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    NatureCard {
                        Text(
                            text = stringResource(R.string.profile_user_details),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow(Icons.Filled.Person, state.name)
                        DetailRow(Icons.Filled.Email, state.email)
                        if (state.phone.isNotBlank()) DetailRow(Icons.Filled.Phone, state.phone)
                        DetailRow(Icons.Filled.Wc, genderLabel(state.gender))
                    }
                }
            }

            item {
                NatureSectionHeader(
                    title = stringResource(R.string.profile_posts_title),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (!state.hasPosts) {
                item {
                    Text(
                        text = stringResource(
                            if (state.isCurrentUser) R.string.profile_posts_empty_mine else R.string.profile_posts_empty_other
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            posts()
        }
        }
    }
}

@Composable
private fun ProfileHero(state: ProfileUiState, onChangePhoto: () -> Unit) {
    val defaultAvatar = if (state.gender == "female") R.drawable.default_pic_empty_female else R.drawable.default_pic_empty_male
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(Forest800, Moss600)))
    ) {
        TopoBackground(modifier = Modifier.matchParentSize(), alpha = 0.10f)
        MountainRange(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val photoLabel = stringResource(R.string.profile_change_photo)
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
                    .then(
                        if (state.isCurrentUser && !state.uploadingPhoto) {
                            Modifier.clickable(role = Role.Button, onClickLabel = photoLabel, onClick = onChangePhoto)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = state.avatar,
                    placeholder = painterResource(id = defaultAvatar),
                    error = painterResource(id = defaultAvatar),
                    fallback = painterResource(id = defaultAvatar),
                    contentDescription = stringResource(
                        if (state.isCurrentUser) R.string.profile_change_photo else R.string.profile_image_desc
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (state.uploadingPhoto) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(PathyColors.Scrim),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                } else if (state.isCurrentUser) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color.Black.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Text(
                text = state.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.email.isNotBlank()) {
                Text(
                    text = state.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.points?.let {
                NatureTag(
                    label = stringResource(R.string.profile_points_count, NumberFormat.getIntegerInstance().format(it)),
                    color = Sand400,
                    icon = Icons.Filled.EmojiEvents,
                    onHero = true
                )
            }
        }
    }
}

private class Tile(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun TileRow(first: Tile, second: Tile) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (tile in listOf(first, second)) {
            NatureActionTile(
                label = tile.label,
                icon = tile.icon,
                onClick = tile.onClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun EmergencyRow(onClick: () -> Unit) {
    NatureCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.profile_sos_button),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
