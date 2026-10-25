package com.example.pathy.data.moderation

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Toxicity scoring through the `moderateText` Cloud Function, which calls the Perspective API.
 *
 * The Perspective key stays on the server. Previously it was sent as a URL query parameter and
 * logged by the HTTP logging interceptor.
 */
object RemoteModeration {

    private const val TAG = "RemoteModeration"

    /** Returns the server decision, or null when the check could not run (the caller decides). */
    suspend fun check(text: String): ModerationDecision? {
        return try {
            val result = FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("moderateText")
                .call(mapOf("text" to text))
                .await()

            when ((result.data as? Map<*, *>)?.get("decision")) {
                "BLOCKED" -> ModerationDecision.BLOCKED
                "NEEDS_EDIT" -> ModerationDecision.NEEDS_EDIT
                "OK" -> ModerationDecision.OK
                else -> null
            }
        } catch (e: FirebaseFunctionsException) {
            Log.w(TAG, "moderateText unavailable: ${e.code}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "moderateText failed: ${e.javaClass.simpleName}")
            null
        }
    }
}
