package com.example.pathy.ui.screens

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.content.Intent
import com.example.pathy.data.toTrailData
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.pathy.ui.design.NatureEmptyState
import com.example.pathy.ui.design.NatureLoadingState
import com.example.pathy.ui.design.pathyTopBarColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.*
import com.example.pathy.data.*
import com.example.pathy.data.EventRepository.uploadEventToFirestore
import com.example.pathy.ui.theme.PathyColors
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.swiperefresh.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun CommunityScreen(
    eventTitle: String? = null,
    eventDescription: String? = null,
    eventStartDate: String? = null,
    eventEndDate: String? = null,
    eventStartTime: String? = null,
    eventEndTime: String? = null,
    eventLocation: String? = null,
    eventImageUris: List<String>? = null,
    isEventRegistrationOpen: Boolean,
    fromEvent: Boolean,
    fromPost: Boolean,
    postText: String? = null,
    trailId: String? = null,
    trailName: String? = null,
    trailRating: Float = 0f,
    postImageUris: List<String>? = null,
    onCreatePostClick: () -> Unit,
    onCreateEventClick: () -> Unit,
) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val comViewModel: CommunityViewModel = viewModel()
    val isLoading by comViewModel.isLoading.collectAsState()
    var isUploadingContent by remember { mutableStateOf(false) }
    var ContentUploadComplete by remember { mutableStateOf(false) }
    val combinedItems by comViewModel.combinedItems.collectAsState()

    LaunchedEffect(ContentUploadComplete) {
        if (ContentUploadComplete) {
            delay(2000)
            ContentUploadComplete = false
        }
    }

    LaunchedEffect(fromPost) {

        if (fromPost && postText != null && trailId != null && trailName != null && postImageUris != null) {
            isUploadingContent = true
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser

            if (user != null) {
                val profile = ProfileDataStore(context).profileFlow.first()
                val userName = profile.name.ifBlank { user.displayName ?: context.getString(R.string.default_user_name) }
                val profileImageUrl = profile.imageUri?.toString() ?: user.photoUrl?.toString()
                ?: "android.resource://${context.packageName}/drawable/default_pic_empty_male"
                val postRef = FirebaseFirestore.getInstance().collection("posts").document()

                fun publishPostWithImages(postImages: List<String>) {
                    val postToUpload = Post(
                        postId = postRef.id,
                        userId = user.uid,
                        userName = userName,
                        userProfileImageUrl = profileImageUrl,
                        trailId = trailId,
                        trailName = trailName,
                        trailRating = trailRating,
                        postText = postText,
                        imageUrls = postImages,
                        likesCount = 0,
                        commentsCount = 0,
                        likedBy = emptyMap(),
                        timestamp = System.currentTimeMillis()
                    )

                    uploadPostToFirestore(
                        post = postToUpload,
                        onSuccess = {
                            ContentUploadComplete = true
                            isUploadingContent = false
                        },
                        onFailure = { e ->
                            isUploadingContent = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.post_upload_failed, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                val imageUris = postImageUris.map { Uri.parse(it) }

                if (imageUris.isNotEmpty()) {
                    uploadImagesList(
                        context = context,
                        uris = imageUris,
                        onComplete = { imageUrls ->
                            publishPostWithImages(imageUrls)
                        },
                        onError = {
                            isUploadingContent = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.images_upload_failed, it.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    publishPostWithImages(emptyList())
                }
            }
        }
    }
    LaunchedEffect(fromEvent) {
        if (fromEvent &&
            eventTitle != null &&
            eventDescription != null &&
            trailId != null &&
            trailName != null &&
            eventStartDate != null &&
            eventEndDate != null &&
            eventStartTime != null &&
            eventEndTime != null &&
            eventImageUris != null
        ) {
            isUploadingContent = true
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser

            if (user != null) {
                val profile = ProfileDataStore(context).profileFlow.first()
                val userName = profile.name.ifBlank { user.displayName ?: context.getString(R.string.default_user_name) }
                val profileImageUrl = profile.imageUri?.toString() ?: user.photoUrl?.toString()
                ?: "android.resource://${context.packageName}/drawable/default_pic_empty_male"
                val eventRef = FirebaseFirestore.getInstance().collection("events").document()

                fun publishEventWithImages(imageUrls: List<String>) {
                    val event = CommunityEvent(
                        id = eventRef.id,
                        title = eventTitle,
                        userName = userName,
                        userProfileImageUrl = profileImageUrl,
                        description = eventDescription,
                        location = eventLocation ?: "",
                        startDate = eventStartDate,
                        endDate = eventEndDate,
                        startTime = eventStartTime,
                        endTime = eventEndTime,
                        trailId = trailId,
                        trailName = trailName,
                        trailRating = trailRating,
                        createdByUid = user.uid,
                        createdAt = System.currentTimeMillis(),
                        imageUrls = imageUrls,
                        registrationOpen = isEventRegistrationOpen,
                        attendees = emptyList()
                    )
                    uploadEventToFirestore(
                        event = event,
                        onSuccess = {
                            ContentUploadComplete = true
                            isUploadingContent = false
                        },
                        onFailure = {
                            isUploadingContent = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.event_create_failed, it.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                val imageUris = eventImageUris.map { Uri.parse(it) }

                if (imageUris.isNotEmpty()) {
                    uploadImagesList(
                        context = context,
                        uris = imageUris,
                        onComplete = { urls -> publishEventWithImages(urls) },
                        onError = {
                            isUploadingContent = false
                            Toast.makeText(context, context.getString(R.string.images_upload_failed, it.message), Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    publishEventWithImages(emptyList())
                }
            }
        }else {
            android.util.Log.w("CommunityScreen", "Event not published: a required value is missing")
        }
    }

    var profileImageUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val profile = ProfileDataStore(context).profileFlow.first()
        profileImageUrl = profile.imageUri?.toString()
            ?: FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                    ?: "android.resource://${context.packageName}/drawable/default_pic_empty_male"
    }
    val loadFailed by comViewModel.loadFailed.collectAsState()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    CommunityContent(
        items = combinedItems,
        isLoading = isLoading,
        loadFailed = loadFailed,
        publishState = when {
            ContentUploadComplete -> PublishState.Done
            isUploadingContent -> PublishState.Publishing
            else -> PublishState.Idle
        },
        publisherAvatar = profileImageUrl,
        onBack = { backDispatcher?.onBackPressed() },
        onNewPost = onCreatePostClick,
        onNewEvent = onCreateEventClick,
        onRefresh = { comViewModel.refresh() }
    ) { item ->
        when (item) {
            is CommunityItem.PostItem -> PostCard(
                post = item.post,
                authViewModel = authViewModel,
                comViewModel = comViewModel,
                onPostClick = { post ->
                    val db = FirebaseFirestore.getInstance()
                    val id = post.trailId
                    if (id.isNotEmpty()) {
                        db.collection("trails")
                            .document(id)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val savedTrail = document.toObject(SavedTrail::class.java)
                                    if (savedTrail != null) {
                                        val trail = savedTrail.toTrailData()
                                        val intent = TrailDetailsActivity.createIntent(context, trail)
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.trail_invalid), Toast.LENGTH_SHORT).show()
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
            )
            is CommunityItem.EventItem -> EventCard(
                event = item.event,
                authViewModel = authViewModel,
                comViewModel = comViewModel,
                onMoreClick = { event ->
                    val intent = Intent(context, EventDetailsActivity::class.java).apply {
                        putExtra("eventId", event.id)
                    }
                    context.startActivity(intent)
                })
        }
    }
}

internal enum class PublishState { Idle, Publishing, Done }

/** The community feed with every state it can be in. The items themselves are drawn by [itemContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityContent(
    items: List<CommunityItem>,
    isLoading: Boolean,
    loadFailed: Boolean,
    publishState: PublishState,
    publisherAvatar: String?,
    onBack: () -> Unit,
    onNewPost: () -> Unit,
    onNewEvent: () -> Unit,
    onRefresh: () -> Unit,
    itemContent: @Composable (CommunityItem) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.community_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = pathyTopBarColors()
            )
        }
    ) { padding ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = isLoading && items.isNotEmpty()),
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { ComposerRow(onNewPost, onNewEvent) }
                if (publishState != PublishState.Idle) {
                    item { PublishBanner(publishState, publisherAvatar) }
                }
                when {
                    items.isNotEmpty() -> items(
                        items,
                        key = {
                            when (it) {
                                is CommunityItem.PostItem -> "post-" + it.post.postId
                                is CommunityItem.EventItem -> "event-" + it.event.id
                            }
                        }
                    ) { itemContent(it) }
                    isLoading -> item { NatureLoadingState(stringResource(R.string.common_loading)) }
                    loadFailed -> item {
                        NatureEmptyState(
                            icon = Icons.Filled.CloudOff,
                            title = stringResource(R.string.community_error_title),
                            message = stringResource(R.string.community_error_message),
                            actionLabel = stringResource(R.string.retry),
                            onAction = onRefresh,
                            isError = true
                        )
                    }
                    else -> item {
                        NatureEmptyState(
                            icon = Icons.Filled.Forum,
                            title = stringResource(R.string.community_empty_title),
                            message = stringResource(R.string.community_empty_message),
                            actionLabel = stringResource(R.string.community_new_post),
                            onAction = onNewPost
                        )
                    }
                }
            }
        }
    }
}

/** New post is the main action; New event is the second. Both are at least 48 dp tall. */
@Composable
private fun ComposerRow(onNewPost: () -> Unit, onNewEvent: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onNewPost, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.community_new_post))
        }
        OutlinedButton(onClick = onNewEvent, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.community_new_event))
        }
    }
}

@Composable
private fun PublishBanner(state: PublishState, avatar: String?) {
    val done = state == PublishState.Done
    Surface(
        color = if (done) PathyColors.Success else PathyColors.ProgressBar,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (avatar != null) {
                Image(
                    painter = rememberAsyncImagePainter(avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (done) R.string.community_published else R.string.community_publishing),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
                if (!done) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = Color.White
                    )
                }
            }
            if (done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun ImageCarousel(imageUrls: List<String>, modifier: Modifier = Modifier) {
    val validImages = imageUrls.filter { it.isNotBlank() }
    if (validImages.isEmpty()) return

    val pagerState = rememberPagerState()
    Column {
        HorizontalPager(
            count = validImages.size,
            state = pagerState,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) { page ->
            val imageUrl = validImages[page]
            val painter = rememberAsyncImagePainter(imageUrl)
            val isLoading = painter.state is coil.compose.AsyncImagePainter.State.Loading

            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(4.dp),
            activeColor = MaterialTheme.colorScheme.primary
        )
    }
}
@Composable
fun ShowConnectionDialog(onDismiss: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.error_slow_connection), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        }
    }
}