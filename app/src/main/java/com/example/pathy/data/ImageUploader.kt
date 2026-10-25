package com.example.pathy.data

import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.pathy.ui.theme.PathyColors
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import java.util.UUID

fun uploadImageToStorage(
    context: Context,
    uri: Uri,
    storagePath: String,
    onSuccess: (String) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val ownerUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    if (ownerUid == null) {
        onFailure(IllegalStateException(context.getString(R.string.not_signed_in_upload)))
        return
    }
    val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)
    // A smaller picture uploads faster on a weak connection; if it cannot be shrunk the original is sent.
    val small = ImageCompressor.compress(context, uri)
    val task = if (small != null) {
        storageRef.putBytes(small, StoragePaths.jpegUploadMetadata(ownerUid))
    } else {
        storageRef.putFile(uri, StoragePaths.jpegUploadMetadata(ownerUid))
    }
    task
        .addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                onSuccess(downloadUrl.toString())
            }
        }
        .addOnFailureListener { exception ->
            onFailure(exception)
        }
}

fun uploadImagesList(
    context: Context,
    uris: List<Uri>,
    onComplete: (List<String>) -> Unit,
    onError: (Exception) -> Unit
) {
    if (uris.isEmpty()) {
        onComplete(emptyList())
        return
    }

    // Uploads run side by side and finish in any order; each url goes back to the place of its picture.
    val imageUrls = arrayOfNulls<String>(uris.size)
    var completed = 0
    var failed = false

    uris.forEachIndexed { index, uri ->
        uploadImageToStorage(
            context = context,
            uri = uri,
            storagePath = StoragePaths.newPostImage(),
            onSuccess = { url ->
                imageUrls[index] = url
                completed++
                if (completed == uris.size && !failed) {
                    onComplete(imageUrls.filterNotNull())
                }
            },
            onFailure = { error ->
                // Report once; a post must not be published with some of its pictures missing.
                if (!failed) {
                    failed = true
                    onError(error)
                }
            }
        )
    }
}
@Composable
fun ImageReplaceDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.profile_change_photo)) },
            text = { Text(stringResource(R.string.image_replace_confirm)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.common_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_no))
                }
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceBottomSheet(
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.image_source_title), style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onPickGallery) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.image_source_gallery), modifier = Modifier.size(48.dp))
                    }
                    Text(stringResource(R.string.image_source_gallery))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onPickCamera) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.image_source_camera), modifier = Modifier.size(48.dp))
                    }
                    Text(stringResource(R.string.image_source_camera))
                }
            }
        }
    }
}
@Composable
fun ImagePickerSection(
    imageUris: List<Uri>,
    onPickImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PathyColors.SurfaceVariant, RoundedCornerShape(12.dp))
                .clickable { onPickImages() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                stringResource(R.string.image_pick_title),
                style = MaterialTheme.typography.bodyLarge.copy(color = PathyColors.Primary)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(imageUris) { uri ->
                Box(modifier = Modifier.size(80.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(24.dp)
                            .background(PathyColors.SurfaceVariant, shape = CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = CircleShape)
                            .clickable { onRemoveImage(uri) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.image_remove_desc),
                            tint = PathyColors.Error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
fun createImageUri(context: Context): Uri {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    return context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ) ?: throw java.io.IOException("Could not create a picture file")
}
