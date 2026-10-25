package com.example.pathy.emergency

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Stores the user's emergency contacts (names and phone numbers) on the device, encrypted with
 * a key kept in the Android Keystore. The plain file used by earlier builds is removed.
 */
object EmergencyContactStore {

    private const val TAG = "EmergencyContactStore"
    private const val PREFS_NAME = "emergency_contacts_secure"
    private const val LEGACY_PREFS_NAME = "emergency_contacts"
    private const val KEY_CONTACTS = "contacts"
    private val listType = object : TypeToken<List<EmergencyContact>>() {}.type

    fun load(context: Context): List<EmergencyContact> {
        removeLegacyPrefs(context)
        val json = prefs(context)?.getString(KEY_CONTACTS, null) ?: return emptyList()
        return runCatching { Gson().fromJson<List<EmergencyContact>>(json, listType) }
            .getOrNull()
            ?: emptyList()
    }

    fun save(context: Context, contacts: List<EmergencyContact>) {
        val editor = prefs(context)?.edit() ?: return
        editor.putString(KEY_CONTACTS, Gson().toJson(contacts)).apply()
    }

    private fun prefs(context: Context): SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore can fail on some devices. Never fall back to plain storage for phone numbers.
        Log.e(TAG, "Encrypted storage unavailable: ${e.javaClass.simpleName}")
        null
    }

    private fun removeLegacyPrefs(context: Context) {
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .takeIf { it.all.isNotEmpty() }
            ?.let { context.deleteSharedPreferences(LEGACY_PREFS_NAME) }
    }
}
