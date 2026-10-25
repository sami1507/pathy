package com.example.pathy

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "login_prefs")

class LoginPreferences(private val context: Context) {
    companion object {
        private val KEY_KEEP_SIGNED_IN = booleanPreferencesKey("keep_signed_in")
    }
    val keepSignedInFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_KEEP_SIGNED_IN] == true }

    suspend fun setKeepSignedIn(keep: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KEEP_SIGNED_IN] = keep
        }
    }
}
object SessionState {
    var hasLaunched: Boolean = false
}