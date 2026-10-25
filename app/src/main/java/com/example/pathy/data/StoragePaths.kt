package com.example.pathy.data

import com.google.firebase.storage.StorageMetadata
import java.util.UUID

/**
 * Single source of truth for Firebase Storage paths. Keep in sync with `storage.rules`.
 *
 *   profileImages/{uid}.jpg      profile picture (one per user)
 *   post_images/{uuid}.jpg       community post images
 *   events/{uuid}.jpg            event images
 *   users/{uid}/...              trail and activity photos
 */
object StoragePaths {

    private const val PROFILE_IMAGES = "profileImages"
    private const val POST_IMAGES = "post_images"
    private const val EVENT_IMAGES = "events"
    private const val USERS = "users"

    fun profileImage(uid: String): String = "$PROFILE_IMAGES/$uid.jpg"

    fun newPostImage(): String = "$POST_IMAGES/${UUID.randomUUID()}.jpg"

    fun newEventImage(): String = "$EVENT_IMAGES/${UUID.randomUUID()}.jpg"

    fun userFile(uid: String, relativePath: String): String = "$USERS/$uid/$relativePath"

    /**
     * Metadata for every upload. Storage rules require `ownerUid` so only the uploader can
     * overwrite or delete the file, and they check the content type and size.
     */
    fun jpegUploadMetadata(ownerUid: String): StorageMetadata =
        StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("ownerUid", ownerUid)
            .build()
}
