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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                            text = stringResource(R.string.community_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        ) { padding ->
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = {
                    comViewModel.listenToPosts()
                    comViewModel.listenToEvents()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionButtonsRow(onCreatePostClick, onCreateEventClick)

                    if (isUploadingContent || ContentUploadComplete) {
                        Surface(
                            color = if (ContentUploadComplete) PathyColors.Success else PathyColors.ProgressBar,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (profileImageUrl != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(profileImageUrl),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (ContentUploadComplete) stringResource(R.string.post_published) else stringResource(R.string.post_publishing),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                    )
                                    if (!ContentUploadComplete) {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PathyColors.Primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        LazyColumn {
                            items(combinedItems) { item ->
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
                    }
                }
            }
        }
    }
}

@Composable
fun GradientOutlineButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(PathyColors.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun ActionButtonsRow(
    onCreatePostClick: () -> Unit,
    onCreateEventClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        GradientOutlineButton(stringResource(R.string.community_new_post), onClick = onCreatePostClick)
        GradientOutlineButton(stringResource(R.string.community_new_event), onClick = onCreateEventClick)
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