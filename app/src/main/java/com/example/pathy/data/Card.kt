package com.example.pathy.data

import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.CommunityEvent
import com.example.pathy.ProfileActivity
import com.example.pathy.R
import com.example.pathy.SignInActivity
import com.example.pathy.data.EventRepository.deleteEventWithImages
import com.example.pathy.i18n.relativeTimeAgo
import com.example.pathy.getDisplayTrailName
import com.example.pathy.ui.screens.ImageCarousel
import com.example.pathy.ui.screens.ShowConnectionDialog
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    event: CommunityEvent,
    authViewModel: AuthViewModel,
    comViewModel: CommunityViewModel,
    onMoreClick: (CommunityEvent) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val defaultProfileImage = "android.resource://${context.packageName}/drawable/default_pic_empty_male"
    val isLiked = currentUser?.uid != null && event.likedBy?.get(currentUser?.uid) == true
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var commentText by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(emptyList<DisplayComment>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var message by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var isPost by remember { mutableStateOf(true) }
    var showConnectionError by remember { mutableStateOf(false) }
    var animateBounce by remember { mutableStateOf(false) }
    val imageModel = if (event.userProfileImageUrl.isNullOrBlank()) {
        defaultProfileImage
    } else {
        event.userProfileImageUrl
    }
    val scale by animateFloatAsState(
        targetValue = if (animateBounce) 1.3f else 1f,
        animationSpec = tween(durationMillis = 300),
        finishedListener = { animateBounce = false }
    )

    DisposableEffect(event.id) {
        val listener = listenToCommentsWithProfiles(
            itemId = event.id,
            collection = "events",
            onResult = { comments = it },
            onError = { }
        )
        onDispose { listener.remove() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 3.dp)
            .clickable { onMoreClick(event) },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(containerColor = PathyColors.AccentContainer)
    ) {
        Box {
            Column {
                Box {
                    if (event.imageUrls.isNotEmpty() && event.imageUrls.all { it.isNotBlank() }) {
                        ImageCarousel(
                            imageUrls = event.imageUrls,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                        )
                    }

                    if (event.createdByUid == currentUser?.uid) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.menu_desc),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.event_delete)) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                event.title.let {
                    Box(
                        modifier = Modifier
                            .background(PathyColors.SandContainer, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "📍 $it",
                            style = MaterialTheme.typography.titleLarge,
                            color = PathyColors.Accent
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🗓 ${event.startDate}",
                        style = MaterialTheme.typography.labelLarge,
                        color = PathyColors.Bark
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageModel),
                        contentDescription = stringResource(R.string.profile_image_desc),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                val intent = Intent(context, ProfileActivity::class.java).apply {
                                    putExtra("userId", event.createdByUid)
                                    putExtra("userName", event.userName)
                                }
                                context.startActivity(intent)
                            }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = event.userName,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (event.description.length > 50) {
                        Text(
                            text = if (expanded) stringResource(R.string.common_show_less) else stringResource(R.string.common_read_more),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { expanded = !expanded },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (index < event.trailRating.roundToInt()) PathyColors.Warning else PathyColors.ProgressBar,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                authViewModel.checkUserAndConnection(
                                    context = context,
                                    onRedirectToLogin = {
                                        context.startActivity(Intent(context, SignInActivity::class.java))
                                    },
                                    onContinueWithWarning = {
                                        currentUser?.uid?.let { toggleLike("events", event.id, it) }
                                        if (!isLiked) animateBounce = true
                                        showConnectionError = true
                                    },
                                    onAllowed = {
                                        currentUser?.uid?.let { toggleLike("events", event.id, it) }
                                        if (!isLiked) animateBounce = true
                                    }
                                )
                            }
                        }) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(R.string.like_desc),
                                tint = if (isLiked) PathyColors.Error else Color.Gray,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            )
                        }
                        Text("❤\uFE0F ${maxOf(event.likesCount, 0)}", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(onClick = {
                            coroutineScope.launch {
                                authViewModel.checkUserAndConnection(
                                    context = context,
                                    onRedirectToLogin = {
                                        context.startActivity(Intent(context, SignInActivity::class.java))
                                    },
                                    onContinueWithWarning = {
                                        showConnectionError = true
                                    },
                                    onAllowed = {
                                        selectedItemId = event.id
                                        isPost = false
                                        showCommentSheet = true
                                    }
                                )
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ChatBubbleOutline,
                                contentDescription = stringResource(R.string.comment_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            "${event.commentsCount}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    comments.forEach { displayComment ->
                        val comment = displayComment.comment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(displayComment.userProfileImageUrl),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayComment.userName, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text(stringResource(R.string.comment_add_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    authViewModel.checkUserAndConnection(
                                        context = context,
                                        onRedirectToLogin = {
                                            context.startActivity(Intent(context, SignInActivity::class.java))
                                            focusManager.clearFocus()
                                        },
                                        onContinueWithWarning = {
                                            showConnectionError = true
                                        },
                                        onAllowed = {}
                                    )
                                }
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            authViewModel.checkUserAndConnection(
                                context = context,
                                onRedirectToLogin = {
                                    context.startActivity(
                                        Intent(
                                            context,
                                            SignInActivity::class.java
                                        )
                                    )
                                },
                                onContinueWithWarning = {
                                    submitComment(
                                        collection = "events",
                                        itemId = event.id,
                                        text = commentText,
                                        onSuccess = { commentText = "" },
                                        onError = {}
                                    )
                                    showConnectionError = true
                                },
                                onAllowed = {
                                    submitComment(
                                        collection = "events",
                                        itemId = event.id,
                                        text = commentText,
                                        onSuccess = { commentText = "" },
                                        onError = {}
                                    )
                                }
                            )
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.End)
                ) {
                    Text(stringResource(R.string.common_send))
                }
                event.registrationOpen.takeIf { it && currentUser?.uid != null }?.let {
                    if (event.attendees.contains(currentUser?.uid) != true) {
                        Button(
                            onClick = {
                                FirebaseFirestore.getInstance()
                                    .collection("events")
                                    .document(event.id)
                                    .update("attendees", FieldValue.arrayUnion(currentUser?.uid))
                            },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(
                                    0xFF46F500
                                )
                                )
                        ) {
                            Text(stringResource(R.string.event_register_button))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.event_already_registered),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                color = PathyColors.Success,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            message = ""
                            if (currentUser != null) {
                                Button(
                                    onClick = {
                                        FirebaseFirestore.getInstance().collection("events")
                                            .document(event.id)
                                            .update(
                                                "attendees",
                                                FieldValue.arrayRemove(currentUser.uid)
                                            )
                                            .addOnSuccessListener {
                                                message = context.getString(R.string.event_unregistered)
                                            }
                                            .addOnFailureListener {
                                                message = context.getString(R.string.event_cancel_failed)
                                            }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Error)
                                ) {
                                    Text(if (message != "") message else stringResource(R.string.event_cancel_registration), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCommentSheet && selectedItemId != null) {
        ModalBottomSheet(
            onDismissRequest = { showCommentSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.95f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.comments_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comments) { displayComment ->
                        val comment = displayComment.comment
                        Row(verticalAlignment = Alignment.Top) {
                            Image(
                                painter = rememberAsyncImagePainter(displayComment.userProfileImageUrl),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = displayComment.userName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = relativeTimeAgo(comment.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(stringResource(R.string.comment_add_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val itemId = selectedItemId
                            if (commentText.isNotBlank() && itemId != null) {
                                submitComment(
                                    collection = if (isPost) "posts" else "events",
                                    itemId = itemId,
                                    text = commentText,
                                    onSuccess = {
                                        commentText = ""
                                    },
                                    onError = {}
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.common_send))
                    }
                }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.post_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    comViewModel.markEventAsDeleting(event.id)

                    coroutineScope.launch {
                        delay(350)
                        deleteEventWithImages(event.id, event.imageUrls)
                    }
                }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showConnectionError) {
        ShowConnectionDialog {
            showConnectionError = false
        }
    }
}

@Composable
fun TrailCard(
    trail: TrailData,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayName = getDisplayTrailName(context, trail)

    val supportsBike = remember(trail) {
        trail.tags.suitableFor.any { it.contains("אופניים") }
    }
    val supportsCar = remember(trail) {
        trail.tags.suitableFor.any { it.contains("רכב") }
    }

    val type = when {
        trail.isHikingTrail -> "hike"
        supportsBike -> "bike"
        supportsCar -> "car"
        else -> "hike"
    }

    var icon: ImageVector = Icons.Filled.DirectionsWalk
    var tint = PathyColors.Title
    var label = stringResource(R.string.field_trail)
    var pinHex = "616161"

    when (type) {
        "hike" -> {
            icon = Icons.Filled.DirectionsWalk
            tint = PathyColors.Title
            label = stringResource(R.string.trail_kind_hiking)
            pinHex = "2E7D32"
        }
        "bike" -> {
            icon = Icons.Filled.PedalBike
            tint = PathyColors.SecondaryDark
            label = stringResource(R.string.trail_kind_bike)
            pinHex = "1976D2"
        }
        "car" -> {
            icon = Icons.Filled.DirectionsCar
            tint = PathyColors.Warning
            label = stringResource(R.string.trail_kind_offroad)
            pinHex = "F57C00"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape  = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        val previewUrl = remember(trail, pinHex) {
            val lat   = trail.midpoint?.latitude
            val lon   = trail.midpoint?.longitude
            val token = context.getString(R.string.mapbox_access_token)
            "https://api.mapbox.com/styles/v1/mapbox/outdoors-v12/static/" +
                    "pin-s+$pinHex($lon,$lat)/$lon,$lat,14,0/600x300?access_token=$token"
        }

        AsyncImage(
            model = previewUrl,
            contentDescription = stringResource(R.string.trail_view_desc),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentScale = ContentScale.Crop
        )

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PathyColors.Primary
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.card_points_and_label, trail.geoPoints.size, label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
