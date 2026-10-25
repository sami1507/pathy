package com.example.pathy.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Phone numbers are private. They are stored at `users/{uid}/private/contact` (owner-only) and
 * indexed by a hash for phone sign-in. Both are maintained by Cloud Functions, so the phone number
 * never appears in the public `users/{uid}` document.
 */
object ContactInfo {

    private const val TAG = "ContactInfo"

    private fun functions() = FirebaseFunctions.getInstance("us-central1")

    /** Saves the current user's phone number privately. Input is validated server-side. */
    suspend fun savePhone(phone: String): Result<Unit> = runCatching {
        functions().getHttpsCallable("saveContactInfo")
            .call(mapOf("phone" to phone))
            .await()
        Unit
    }

    /** Resolves the sign-in email for a phone number. Returns null when no account matches. */
    suspend fun resolveLoginEmail(phone: String): String? {
        val result = functions().getHttpsCallable("resolveLoginEmail")
            .call(mapOf("phone" to phone))
            .await()
        return (result.data as? Map<*, *>)?.get("email") as? String
    }

    /** Reads the signed-in user's own phone number from the private document. */
    suspend fun ownPhone(uid: String): String? {
        val doc = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("private").document("contact")
            .get().await()
        return doc.getString("phone")
    }

    /**
     * One-time cleanup for accounts created before phone numbers were made private:
     * moves the phone into the private document and removes the public `email` and `phone` fields.
     */
    suspend fun migrateLegacyFieldsIfNeeded() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)
        try {
            val doc = userRef.get().await()
            val legacyPhone = doc.getString("phone")
            val hasLegacyEmail = doc.contains("email")
            if (legacyPhone.isNullOrBlank() && !hasLegacyEmail) return

            if (!legacyPhone.isNullOrBlank()) {
                savePhone(legacyPhone).getOrThrow()
            }
            userRef.update(
                mapOf(
                    "phone" to FieldValue.delete(),
                    "email" to FieldValue.delete()
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Legacy contact migration skipped: ${e.javaClass.simpleName}")
        }
    }
}
