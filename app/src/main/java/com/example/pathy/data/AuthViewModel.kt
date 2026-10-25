package com.example.pathy.data

import com.example.pathy.R
import android.content.Context
import android.net.Uri
import android.util.Log
import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import com.example.pathy.i18n.AppLocale
import com.example.pathy.LoginPreferences
import com.example.pathy.SessionState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Resolved when called, so messages follow the language the user has chosen right now. */
    private fun str(@StringRes id: Int, vararg args: Any?): String =
        AppLocale.wrap(getApplication<Application>()).getString(id, *args)

    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            onError(str(R.string.auth_fill_all_fields))
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        // Move legacy public phone/email fields to the private document (runs once per account)
                        CoroutineScope(Dispatchers.IO).launch { ContactInfo.migrateLegacyFieldsIfNeeded() }
                        onSuccess()
                    } else {
                        user?.sendEmailVerification()
                        onError(str(R.string.auth_email_unverified_link_sent))
                    }
                } else {
                    val errorCode = (task.exception as? FirebaseAuthException)?.errorCode

                    val errorMessage = when (errorCode) {
                        "ERROR_USER_NOT_FOUND",
                        "ERROR_WRONG_PASSWORD",
                        "ERROR_INVALID_EMAIL" -> str(R.string.auth_invalid_credentials)
                        "ERROR_USER_DISABLED" -> str(R.string.auth_user_disabled)
                        "ERROR_TOO_MANY_REQUESTS" -> str(R.string.auth_too_many_requests)
                        "ERROR_NETWORK_REQUEST_FAILED" -> str(R.string.error_network_check)
                        else -> str(R.string.auth_sign_in_failed)
                    }

                    onError(errorMessage)
                }
            }
    }

    fun firebaseAuthWithGoogle(
        idToken: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        onSuccess()

                        val firestore = FirebaseFirestore.getInstance()
                        val userRef = firestore.collection("users").document(user.uid)

                        userRef.get()
                            .addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    val newUserData = mapOf(
                                        "uid" to user.uid,
                                        "name" to (user.displayName ?: ""),
                                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                                        "createdAt" to Timestamp.now(),
                                        "reportPoints" to 0L
                                    )
                                    userRef.set(newUserData)
                                }
                            }
                            .addOnFailureListener { e ->
                            }

                    } else {
                        onError(str(R.string.auth_user_missing))
                    }
                } else {
                    onError(task.exception?.localizedMessage ?: str(R.string.auth_google_failed))
                }
            }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        gender : String,
        phone: String,
        imageUri: Uri?,
        createdAt: Timestamp,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signOut()
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = auth.currentUser
                user?.reload()?.addOnCompleteListener {
                    val refreshedUser = auth.currentUser
                    if (refreshedUser != null) {
                        refreshedUser.updateProfile(userProfileChangeRequest {
                            this.displayName = displayName
                        })

                        refreshedUser.sendEmailVerification()
                            .addOnSuccessListener {
                                Log.d("EmailVerify", "Verification email sent")
                            }
                            .addOnFailureListener {
                                Log.e("EmailVerify", "Failed to send email: ${it.message}")
                            }
                        val reportPoints = 0L
                        if (imageUri != null) {
                            val storageRef = FirebaseStorage.getInstance()
                                .reference.child(StoragePaths.profileImage(refreshedUser.uid))

                            storageRef.putFile(imageUri, StoragePaths.jpegUploadMetadata(refreshedUser.uid))
                                .continueWithTask { task ->
                                    if (!task.isSuccessful) {
                                        task.exception?.let { throw it }
                                    }
                                    storageRef.downloadUrl
                                }.addOnSuccessListener { uri ->
                                    saveUserToFirestore(
                                        refreshedUser.uid,
                                        displayName,
                                        gender,
                                        phone,
                                        uri.toString(),
                                        createdAt,
                                        reportPoints,
                                        onSuccess,
                                        onError
                                    )
                                }.addOnFailureListener {
                                    onError(str(R.string.auth_photo_upload_failed, it.localizedMessage))
                                }
                        } else {
                            saveUserToFirestore(
                                refreshedUser.uid,
                                displayName,
                                gender,
                                phone,
                                imageUri?.toString() ?: "",
                                createdAt,
                                reportPoints,
                                onSuccess,
                                onError
                            )
                        }
                    }
                }
            }
            .addOnFailureListener {
                onError(it.localizedMessage ?: str(R.string.auth_sign_up_failed))
            }

    }

    /**
     * Writes the public profile. The email comes from FirebaseAuth and the phone number is stored
     * privately through [ContactInfo], so neither is written to the public `users` document.
     */
    private fun saveUserToFirestore(
        uid: String,
        name: String,
        gender: String,
        phone: String,
        photoUrl: String,
        createdAt: Timestamp,
        reportPoints: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val userRef = firestore.collection("users").document(uid)

        val userData = mapOf(
            "uid" to uid,
            "name" to name,
            "gender" to gender,
            "photoUrl" to photoUrl,
            "createdAt" to createdAt,
            "reportPoints" to reportPoints
        )

        userRef.set(userData)
            .addOnSuccessListener {
                if (phone.count { it.isDigit() } >= 7) {
                    CoroutineScope(Dispatchers.IO).launch { ContactInfo.savePhone(phone) }
                }
                onSuccess()
            }
            .addOnFailureListener { e -> onError(str(R.string.auth_save_user_failed, e.message)) }
    }


    fun forgotPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (email.isBlank()) {
            onError(str(R.string.auth_enter_email_for_reset))
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onError(task.exception?.localizedMessage ?: str(R.string.auth_reset_failed))
                }
            }
    }

    fun ensureUserProfileExists(
        user: FirebaseUser,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("users").document(user.uid)

        userDocRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val newUserData = hashMapOf(
                        "uid" to user.uid,
                        "name" to (user.displayName ?: ""),
                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                        "createdAt" to Timestamp.now(),
                        "reportPoints" to 0L
                    )
                    userDocRef.set(newUserData)
                        .addOnSuccessListener {
                            onComplete() }
                        .addOnFailureListener { e ->
                            onError(e)
                        }
                } else {
                    onComplete()
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    fun isUserSignedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    suspend fun hasReliableInternet(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("https://clients3.google.com/generate_204")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connect()
            conn.responseCode == 204
        } catch (e: Exception) {
            Log.e("NetworkCheck", "Connection check failed", e)
            false
        }
    }
    suspend fun checkUserAndConnection(
        context: Context,
        onAllowed: () -> Unit,
        onRedirectToLogin: () -> Unit,
        onContinueWithWarning: () -> Unit
    ) {
        if (!isUserSignedIn()) {
            onRedirectToLogin()
        } else {
            val hasGoodConnection = hasReliableInternet()
            if (hasGoodConnection) {
                onAllowed()
            } else {
                onContinueWithWarning()
            }
        }
    }
}

/**
 * Returns the login email for the input. An email address is used as-is. Anything else is treated
 * as a phone number and resolved by the rate-limited `resolveLoginEmail` Cloud Function, because
 * phone numbers are no longer readable from the public users collection.
 */
suspend fun resolveEmailFromInput(input: String): String? {
    val trimmedInput = input.trim()

    if (android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedInput).matches()) {
        return trimmedInput
    }

    return ContactInfo.resolveLoginEmail(trimmedInput)
}


suspend fun performLoadingChecks(context: Context): Boolean {
    val auth = FirebaseAuth.getInstance()
    val prefs = LoginPreferences(context)
    val keepSigned = prefs.keepSignedInFlow.first()

    try {
        auth.currentUser?.reload()
    } catch (_: Exception) {
        auth.signOut()
    }

    val user = auth.currentUser
    return (keepSigned || SessionState.hasLaunched) && user != null && user.isEmailVerified
}

fun logout(context: Context) {
    FirebaseAuth.getInstance().signOut()

    val store = ProfileDataStore(context)
    val prefs = LoginPreferences(context)

    CoroutineScope(Dispatchers.IO).launch {
        store.clearProfile()
        prefs.setKeepSignedIn(false)
    }
}
