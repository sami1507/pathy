package com.example.pathy.screens

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material3.LocalTextStyle
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.CommunityActivity
import com.example.pathy.NoTrailsDialog
import com.example.pathy.R
import com.example.pathy.SignedOutNotice
import com.example.pathy.data.ImageSourceBottomSheet
import com.example.pathy.data.ProfileDataStore
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.createImageUri
import com.example.pathy.data.moderation.ModerationDecision
import com.example.pathy.data.moderation.ModerationPipeline
import com.example.pathy.formatDifficulty
import com.example.pathy.ui.screens.ShowConnectionDialog
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MAX_POST_CHARS = 500
const val MAX_POST_PHOTOS = 5

/** What stops a post from being published yet, in the order a person would fix it. */
enum class PostGap { TRAIL, TEXT }

fun postGap(hasTrail: Boolean, text: String): PostGap? = when {
    !hasTrail -> PostGap.TRAIL
    text.isBlank() -> PostGap.TEXT
    else -> null
}

@Composable
fun CreatePostScreen(
    selectedTrail: SavedTrail?,
    onSelectTrailClick: () -> Unit
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    if (userId == null) {
        SignedOutNotice()
        return
    }
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var postText by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoSource by remember { mutableStateOf(false) }
    var showNoTrails by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    var showConnectionError by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var authorName by remember { mutableStateOf<String?>(null) }
    var authorImage by remember { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) photos = (photos + uris).distinct().take(MAX_POST_PHOTOS)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { photos = (photos + it).take(MAX_POST_PHOTOS) }
    }

    LaunchedEffect(Unit) {
        val profile = ProfileDataStore(context).profileFlow.first()
        val user = FirebaseAuth.getInstance().currentUser
        authorName = profile.name.ifBlank { user?.displayName ?: context.getString(R.string.default_user_name) }
        authorImage = profile.imageUri?.toString() ?: user?.photoUrl?.toString()
        if ((context as? Activity)?.intent?.getBooleanExtra("showConnectionDialog", false) == true) showConnectionError = true
    }

    val hasContent = postText.isNotBlank() || photos.isNotEmpty()
    val gap = postGap(selectedTrail != null, postText)
    BackHandler { if (hasContent && !checking) showExit = true else (context as? Activity)?.finish() }

    fun publish(trail: SavedTrail) {
        val intent = Intent(context, CommunityActivity::class.java).apply {
            putExtra("fromPost", true)
            putExtra("postText", postText.trim())
            putExtra("trailId", trail.id.ifBlank { "unknown" })
            putExtra("trailName", trail.name.ifBlank { context.getString(R.string.trail_unnamed) })
            putExtra("trailRating", trail.rating)
            putStringArrayListExtra("postImageUris", ArrayList(photos.map { it.toString() }))
        }
        context.startActivity(intent)
        (context as? Activity)?.finish()
    }

    fun onPublishClick() {
        val trail = selectedTrail ?: return
        if (gap != null || checking) return
        scope.launch {
            checking = true
            try {
                val decision = try {
                    withContext(Dispatchers.IO) { ModerationPipeline.moderate(postText) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The check is unavailable right now: the post goes on, the person is told why.
                    Toast.makeText(context, context.getString(R.string.moderation_unavailable), Toast.LENGTH_LONG).show()
                    ModerationDecision.OK
                }
                when (decision) {
                    ModerationDecision.BLOCKED -> Toast.makeText(context, context.getString(R.string.moderation_blocked), Toast.LENGTH_LONG).show()
                    ModerationDecision.NEEDS_EDIT -> Toast.makeText(context, context.getString(R.string.moderation_rephrase), Toast.LENGTH_LONG).show()
                    ModerationDecision.OK -> publish(trail)
                }
            } finally {
                checking = false
            }
        }
    }

    NoTrailsDialog(show = showNoTrails, onDismiss = { showNoTrails = false })
    if (showConnectionError) ShowConnectionDialog { showConnectionError = false }

    CreatePostContent(
        authorName = authorName,
        authorImage = authorImage,
        trail = selectedTrail,
        text = postText,
        onTextChange = { if (it.length <= MAX_POST_CHARS) postText = it },
        photos = photos,
        onAddPhoto = { showPhotoSource = true },
        onRemovePhoto = { photos = photos - it },
        gap = gap,
        checking = checking,
        onBack = { if (hasContent && !checking) showExit = true else (context as? Activity)?.finish() },
        onChooseTrail = {
            db.collection("users").document(userId).collection("trails_user").limit(1).get()
                .addOnSuccessListener { result -> if (!result.isEmpty) onSelectTrailClick() else showNoTrails = true }
                .addOnFailureListener { Toast.makeText(context, context.getString(R.string.trails_load_failed_retry), Toast.LENGTH_SHORT).show() }
        },
        onPublish = ::onPublishClick
    )

    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(stringResource(R.string.confirm_exit_title)) },
            text = { Text(stringResource(R.string.post_discard_confirm)) },
            confirmButton = {
                TextButton(onClick = { showExit = false; (context as? Activity)?.finish() }) {
                    Text(stringResource(R.string.common_discard), color = PathyColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.common_continue)) }
            }
        )
    }

    if (showPhotoSource) {
        ImageSourceBottomSheet(
            onDismiss = { showPhotoSource = false },
            onPickGallery = { showPhotoSource = false; gallery.launch("image/*") },
            onPickCamera = {
                showPhotoSource = false
                val uri = createImageUri(context)
                cameraUri = uri
                camera.launch(uri)
            }
        )
    }
}

/** The screen itself, with no database or permission work, so it can be drawn and tested on its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostContent(
    authorName: String?,
    authorImage: String?,
    trail: SavedTrail?,
    text: String,
    onTextChange: (String) -> Unit,
    photos: List<Uri>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    gap: PostGap?,
    checking: Boolean,
    onBack: () -> Unit,
    onChooseTrail: () -> Unit,
    onPublish: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.community_new_post), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = pathyTopBarColors()
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gap?.let {
                        Text(
                            text = stringResource(if (it == PostGap.TRAIL) R.string.post_need_trail else R.string.post_need_text),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onPublish,
                        enabled = gap == null && !checking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.Primary)
                    ) {
                        if (checking) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.post_checking))
                        } else {
                            Text(stringResource(R.string.post_publish_button), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AuthorRow(authorName, authorImage)
            TrailPicker(trail, onChooseTrail)

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                placeholder = { Text(stringResource(R.string.post_text_hint)) },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                supportingText = {
                    val nearLimit = text.length >= MAX_POST_CHARS * 0.9
                    Text(
                        text = stringResource(R.string.post_counter, text.length, MAX_POST_CHARS),
                        color = if (nearLimit) PathyColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PathyColors.Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            PhotoStrip(photos, onAddPhoto, onRemovePhoto)
        }
    }
}

@Composable
private fun AuthorRow(name: String?, image: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = rememberAsyncImagePainter(image ?: ""),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (name != null) stringResource(R.string.post_as, name) else "",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TrailPicker(trail: SavedTrail?, onChoose: () -> Unit) {
    val context = LocalContext.current
    if (trail == null) {
        Surface(
            onClick = onChoose,
            shape = RoundedCornerShape(14.dp),
            color = PathyColors.Primary.copy(alpha = 0.08f),
            border = BorderStroke(1.5.dp, PathyColors.Primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = PathyColors.Primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.post_trail_choose), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PathyColors.Primary)
            }
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = PathyColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    trail.name.ifBlank { stringResource(R.string.trail_unnamed) },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Straighten, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.unit_km, String.format(java.util.Locale.getDefault(), "%.1f", trail.distanceInMeters / 1000)) +
                            " · " + formatDifficulty(context, trail.difficulty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onChoose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.post_trail_change)) }
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<Uri>, onAdd: () -> Unit, onRemove: (Uri) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.field_photos), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.post_photos_added, photos.size, MAX_POST_PHOTOS),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (photos.size < MAX_POST_PHOTOS) {
                item {
                    Surface(
                        onClick = onAdd,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(96.dp)
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = PathyColors.Primary, modifier = Modifier.size(28.dp))
                            Text(stringResource(R.string.post_add_photo), fontSize = 12.sp, color = PathyColors.Primary)
                        }
                    }
                }
            }
            itemsIndexed(photos, key = { _, uri -> uri.toString() }) { index, uri ->
                Box(Modifier.size(96.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                    // The visible button is small; the touch area around it is the standard 48 dp.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                            .clickable(role = Role.Button) { onRemove(uri) },
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Box(
                            Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.post_remove_photo, index + 1),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
