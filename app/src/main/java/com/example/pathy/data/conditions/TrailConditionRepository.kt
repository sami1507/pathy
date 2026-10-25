package com.example.pathy.data.conditions

import com.example.pathy.core.trail.ConditionReport
import com.example.pathy.core.trail.ConditionStatus
import com.example.pathy.core.trail.ConditionSummary
import com.example.pathy.core.trail.TrailCondition
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Hikers' reports on whether a trail is open: `trail_conditions/<trail>/reports/<user>`.
 * One document per person and trail, so a newer report from the same person replaces their older one
 * and no one can bury a trail in reports.
 */
class TrailConditionRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    /** The summary of recent reports, or null when there are none or they cannot be loaded. */
    suspend fun summary(trailId: String): ConditionSummary? {
        val key = safeKey(trailId) ?: return null
        return try {
            val snapshot = db.collection("trail_conditions").document(key).collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .await()
            val reports = snapshot.documents.mapNotNull { doc ->
                val status = doc.getString("status")?.let { runCatching { ConditionStatus.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
                val at = doc.getLong("createdAt") ?: return@mapNotNull null
                ConditionReport(status, at, doc.getString("userId") ?: doc.id)
            }
            TrailCondition.summarize(reports, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Saves the signed-in person's report. Returns false on any failure. */
    suspend fun report(trailId: String, userId: String, status: ConditionStatus, note: String): Boolean {
        val key = safeKey(trailId) ?: return false
        val data = mutableMapOf<String, Any>(
            "userId" to userId,
            "status" to status.name,
            "createdAt" to System.currentTimeMillis()
        )
        note.trim().take(200).takeIf { it.isNotEmpty() }?.let { data["note"] = it }
        return try {
            db.collection("trail_conditions").document(key).collection("reports").document(userId).set(data).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** Trail ids come from map data, other apps' files and our own saves; only plain ones are used as document ids. */
        fun safeKey(trailId: String): String? =
            trailId.takeIf { it.isNotBlank() && it.length <= 100 && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
    }
}
