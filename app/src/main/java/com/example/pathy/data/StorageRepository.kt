package com.example.pathy.data

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object StorageRepository {

    private const val TAG = "StorageRepository"

    /** Replaces the signed-in user's profile picture and returns its download URL. */
    suspend fun uploadProfileImageToStorage(uri: Uri): String? {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val storageRef = FirebaseStorage.getInstance().reference.child(StoragePaths.profileImage(userId))

        try {
            storageRef.delete().await()
        } catch (e: Exception) {
            // First upload: there is no previous picture to delete
            Log.d(TAG, "No previous profile image to delete")
        }

        return try {
            storageRef.putFile(uri, StoragePaths.jpegUploadMetadata(userId)).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Profile image upload failed: ${e.javaClass.simpleName}")
            null
        }
    }
}
