package com.example.pathy.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes the public profile at `users/{uid}`. The phone number is private (see [ContactInfo]),
 * and the email is taken from FirebaseAuth, so neither is stored in the public document.
 */
object ProfileRepository {

    private const val TAG = "ProfileRepository"

    private val usersCollection get() = FirebaseFirestore.getInstance().collection("users")

    suspend fun getCloudProfileImageUri(userId: String): Uri? {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            snapshot.getString("profileImageUrl")?.let { Uri.parse(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Profile image lookup failed: ${e.javaClass.simpleName}")
            null
        }
    }

    fun UpdateProfileToFirestore(
        userId: String,
        profile: ProfileInfo,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val updateMap = mapOf(
            "name" to profile.name,
            "gender" to profile.gender,
            "profileImageUrl" to (profile.imageUri?.toString() ?: "")
        )

        usersCollection.document(userId).update(updateMap)
            .addOnSuccessListener {
                if (profile.phone.count { it.isDigit() } >= 7) {
                    CoroutineScope(Dispatchers.IO).launch { ContactInfo.savePhone(profile.phone) }
                }
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    suspend fun loadAndSaveProfileFromFirestore(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val store = ProfileDataStore(context)

        val profile = getProfileFromFirestore(user.uid)
        if (profile != null) {
            store.saveName(profile.name)
            store.saveEmail(profile.email)
            store.savePhone(profile.phone)
            store.saveGender(profile.gender)
            store.saveImageUri(profile.imageUri)
            store.saveCreatedAt(profile.createdAt)
        } else {
            Log.e(TAG, "Profile not found for the signed-in user")
        }
    }

    /**
     * Loads a profile. Email and phone are only filled in when [userId] is the signed-in user;
     * other users never receive them.
     */
    suspend fun getProfileFromFirestore(userId: String): ProfileInfo? {
        return try {
            val doc = usersCollection.document(userId).get().await()
            if (!doc.exists()) return null

            val currentUser = FirebaseAuth.getInstance().currentUser
            val isOwner = currentUser?.uid == userId

            ProfileInfo(
                name = doc.getString("name") ?: "",
                email = if (isOwner) currentUser?.email.orEmpty() else "",
                phone = if (isOwner) runCatching { ContactInfo.ownPhone(userId) }.getOrNull().orEmpty() else "",
                gender = doc.getString("gender") ?: "male",
                imageUri = doc.getString("profileImageUrl")?.let { Uri.parse(it) },
                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Profile load failed: ${e.javaClass.simpleName}")
            null
        }
    }
}
