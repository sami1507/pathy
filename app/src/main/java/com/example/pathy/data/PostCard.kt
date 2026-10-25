package com.example.pathy.data

import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.ProfileActivity
import com.example.pathy.R
import com.example.pathy.SignInActivity
import com.example.pathy.i18n.RelativeTime
import com.example.pathy.ui.screens.ShowConnectionDialog
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** People write in their own language whatever the app language is: each text lines up by its own first letter. */
private val UserText = TextStyle(textDirection = TextDirection.Content)

/**
 * A community post, as it appears in the feed. Who wrote it and when, which trail it is about, the text,
 * the photos, and likes and comments - in that order, top to bottom. Comments are written in a sheet, not
 * in every card, so the feed stays easy to scroll.
 */
@Composable
fun PostCard(
    post: Post,
    authViewModel: AuthViewModel,
    comViewModel: CommunityViewModel,
    onPostClick: (Post) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val isLiked = currentUserId != null && post.likedBy[currentUserId] == true
    var comments by remember { mutableStateOf<List<DisplayComment>>(emptyList()) }
    var showComments by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showConnectionError by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var bounce by remember { mutableStateOf(false) }

    LaunchedEffect(bounce) {
        if (bounce) {
            delay(80)
            bounce = false
        }
    }
    DisposableEffect(post.postId) {
        val listener = listenToCommentsWithProfiles(
            itemId = post.postId,
            collection = "posts",
            onResult = { comments = it },
            onError = { }
        )
        onDispose { listener.remove() }
    }

    /** Runs [action] once the person is known to be signed in; offline it still runs, with a notice. */
    fun whenSignedIn(action: () -> Unit) {
        scope.launch {
            authViewModel.checkUserAndConnection(
                context = context,
                onRedirectToLogin = { context.startActivity(Intent(context, SignInActivity::class.java)) },
                onContinueWithWarning = {
                    action()
                    showConnectionError = true
                },
                onAllowed = { action() }
            )
        }
    }

    PostCardContent(
        post = post,
        timeText = RelativeTime.format(context, post.timestamp),
        isLiked = isLiked,
        isOwner = currentUserId != null && post.userId == currentUserId,
        likeScaled = bounce,
        lastComment = comments.lastOrNull(),
        onProfileClick = {
            context.startActivity(Intent(context, ProfileActivity::class.java).apply {
                putExtra("userId", post.userId)
                putExtra("userName", post.userName)
            })
        },
        onTrailClick = { onPostClick(post) },
        onLikeClick = {
            whenSignedIn {
                currentUserId?.let { toggleLike("posts", post.postId, it) }
                if (!isLiked) bounce = true
            }
        },
        onCommentsClick = { whenSignedIn { showComments = true } },
        onDeleteClick = { showDelete = true },
        onPhotoClick = { viewerIndex = it }
    )

    if (showComments) {
        CommentsSheet(
            comments = comments,
            onDismiss = { showComments = false },
            onSend = { text, done ->
                whenSignedIn {
                    submitComment(collection = "posts", itemId = post.postId, text = text, onSuccess = done, onError = {})
                }
            }
        )
    }

    viewerIndex?.let { start ->
        PhotoViewer(urls = post.imageUrls.filter { it.isNotBlank() }, startIndex = start, onClose = { viewerIndex = null })
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.post_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    comViewModel.markPostAsDeleting(post.postId)
                    scope.launch {
                        delay(350) // let the card fade out first
                        deletePostWithImages(post.postId, post.imageUrls)
                    }
                }) { Text(stringResource(R.string.common_delete), color = PathyColors.Error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (showConnectionError) ShowConnectionDialog { showConnectionError = false }
}

/** The card with no database or network work in it, so it can be drawn and tested on its own. */
@Composable
fun PostCardContent(
    post: Post,
    timeText: String,
    isLiked: Boolean,
    isOwner: Boolean,
    likeScaled: Boolean,
    lastComment: DisplayComment?,
    onProfileClick: () -> Unit,
    onTrailClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    val photos = post.imageUrls.filter { it.isNotBlank() }
    val avatar = post.userProfileImageUrl.takeIf { it.isNotBlank() }
        ?: "android.resource://${LocalContext.current.packageName}/drawable/default_pic_empty_male"

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(bottom = 6.dp)) {
            // Who and when
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberAsyncImagePainter(avatar),
                    contentDescription = stringResource(R.string.profile_image_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button, onClick = onProfileClick)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(timeText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isOwner) OwnerMenu(onDeleteClick)
            }

            // The trail it is about
            if (post.trailName.isNotBlank()) {
                TrailChip(post.trailName, post.trailRating, onTrailClick, Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp))
            }

            // The words
            if (post.postText.isNotBlank()) {
                ExpandableText(post.postText, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }

            // The pictures
            if (photos.isNotEmpty()) {
                PostPhotos(photos, onPhotoClick, Modifier.padding(top = 4.dp))
            }

            // Like and comment
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val scale by animateFloatAsState(if (likeScaled) 1.3f else 1f, tween(80), label = "like")
                ActionButton(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    tint = if (isLiked) PathyColors.Error else MaterialTheme.colorScheme.onSurfaceVariant,
                    count = post.likesCount.coerceAtLeast(0),
                    description = stringResource(R.string.post_like_count, post.likesCount.coerceAtLeast(0)),
                    onClick = onLikeClick,
                    iconModifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    icon = Icons.Filled.ChatBubbleOutline,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    count = post.commentsCount.coerceAtLeast(0),
                    description = stringResource(R.string.post_comment_count_desc, post.commentsCount.coerceAtLeast(0)),
                    onClick = onCommentsClick
                )
            }

            // The latest comment, and the way to read the rest
            lastComment?.let {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.userName) }
                        append("  ")
                        append(it.comment.text)
                    },
                    fontSize = 14.sp,
                    style = UserText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }
            if (post.commentsCount > 0) {
                Text(
                    text = stringResource(R.string.post_comments_count, post.commentsCount),
                    fontSize = 13.sp,
                    color = PathyColors.Primary,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onCommentsClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun OwnerMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_desc))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.post_delete), color = PathyColors.Error) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

@Composable
private fun TrailChip(name: String, rating: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.post_open_trail, name)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = PathyColors.Primary.copy(alpha = 0.10f),
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { contentDescription = label }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = PathyColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PathyColors.PrimaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp)
            )
            if (rating > 0f) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Star, contentDescription = null, tint = PathyColors.Warning, modifier = Modifier.size(15.dp))
                Text(String.format(java.util.Locale.getDefault(), "%.1f", rating), fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PathyColors.PrimaryDark, modifier = Modifier.size(18.dp))
        }
    }
}

/** Shows five lines and a "Read more" only when the text really is longer than that. */
@Composable
private fun ExpandableText(text: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            style = UserText,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        if (overflows || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.common_show_less else R.string.common_read_more),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PathyColors.Primary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun PostPhotos(urls: List<String>, onClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState(pageCount = { urls.size })
    Box(modifier) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 14.dp),
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val description = stringResource(R.string.post_photo_of, page + 1, urls.size)
            Image(
                painter = rememberAsyncImagePainter(urls[page]),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Button) { onClick(page) }
            )
        }
        if (urls.size > 1) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 12.dp).clearAndSetSemantics { }
            ) {
                Text(
                    text = "${pager.currentPage + 1}/${urls.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Int,
    description: String,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier
) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = iconModifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** All the comments, newest at the bottom, with the box to write one pinned under them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(comments: List<DisplayComment>, onDismiss: () -> Unit, onSend: (String, () -> Unit) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            Text(
                stringResource(R.string.post_comments_count, comments.size),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (comments.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.post_no_comments), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(comments, key = { it.comment.commentId.ifBlank { it.comment.timestamp.toString() + it.comment.userId } }) { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Image(
                                painter = rememberAsyncImagePainter(item.userProfileImageUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.userName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        RelativeTime.format(context, item.comment.timestamp),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(item.comment.text, fontSize = 14.sp, style = UserText)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    placeholder = { Text(stringResource(R.string.comment_add_hint)) },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    shape = RoundedCornerShape(26.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { onSend(text.trim()) { text = "" } },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.post_send_comment),
                        tint = if (text.isNotBlank()) PathyColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A photo on the whole screen; swipe for the others. */
@Composable
private fun PhotoViewer(urls: List<String>, startIndex: Int, onClose: () -> Unit) {
    if (urls.isEmpty()) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.lastIndex), pageCount = { urls.size })
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Image(
                    painter = rememberAsyncImagePainter(urls[page]),
                    contentDescription = stringResource(R.string.post_photo_of, page + 1, urls.size),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
            }
            if (urls.size > 1) {
                Text(
                    "${pager.currentPage + 1}/${urls.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                )
            }
        }
    }
}
