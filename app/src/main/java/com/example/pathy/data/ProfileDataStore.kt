package com.example.pathy.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

data class ProfileInfo(
    val name: String,
    val email: String,
    val phone: String,
    val imageUri: Uri? = null,
    val gender: String = "male",
    val createdAt: Timestamp
)

class ProfileDataStore(private val context: Context) {

    companion object {
        private val KEY_NAME = stringPreferencesKey("profile_name")
        private val KEY_EMAIL = stringPreferencesKey("profile_email")
        private val KEY_PHONE = stringPreferencesKey("profile_phone")
        private val KEY_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        private val KEY_GENDER = stringPreferencesKey("profile_gender")
        private val KEY_CREATED_AT = longPreferencesKey("profile_created_at")

    }

    val profileFlow: Flow<ProfileInfo> = context.dataStore.data
        .map { prefs ->
            val millis = prefs[KEY_CREATED_AT] ?: System.currentTimeMillis()
            val createdAt =
                Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

            ProfileInfo(
                name     = prefs[KEY_NAME]      ?: "",
                email    = prefs[KEY_EMAIL]     ?: "",
                phone    = prefs[KEY_PHONE]     ?: "",
                imageUri = prefs[KEY_IMAGE_URI]?.let { Uri.parse(it) },
                gender   = prefs[KEY_GENDER]    ?: "male",
                createdAt = createdAt
            )
        }

    fun getNameFlow(): Flow<String> =
        context.dataStore.data.map { it[KEY_NAME] ?: "" }

    suspend fun saveName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAME] = name
        }
    }

    fun getEmailFlow(): Flow<String> =
        context.dataStore.data.map { it[KEY_EMAIL] ?: "" }

    suspend fun saveEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = email
        }
    }

    fun getPhoneFlow(): Flow<String> =
        context.dataStore.data.map { it[KEY_PHONE] ?: "" }

    suspend fun savePhone(phone: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PHONE] = phone
        }
    }

    fun getImageUriFlow(): Flow<Uri?> =
        context.dataStore.data.map { it[KEY_IMAGE_URI]?.let { uri -> Uri.parse(uri) } }

    suspend fun saveImageUri(uri: Uri?) {
        context.dataStore.edit { prefs ->
            if (uri != null) {
                prefs[KEY_IMAGE_URI] = uri.toString()
            } else {
                prefs.remove(KEY_IMAGE_URI)
            }
        }
    }

    fun getGenderFlow(): Flow<String> =
        context.dataStore.data.map { it[KEY_GENDER] ?: "male" }

    suspend fun saveGender(gender: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GENDER] = gender
        }
    }

    suspend fun saveCreatedAt(timestamp: Timestamp) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CREATED_AT] = timestamp.seconds * 1000 + timestamp.nanoseconds / 1_000_000
        }
    }

    suspend fun clearProfile() {
        context.dataStore.edit {
            it.clear()
        }
    }
}
fun copyUriToInternalStorage(context: Context, uri: Uri): Uri {
    val file = File(context.filesDir, "profile_image.jpg")
    if (file.exists()) file.delete()
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    return Uri.fromFile(file)
}
