package com.example.pathy

import android.content.Context
import android.location.Location
import com.example.pathy.data.Group
import com.example.pathy.data.OfflineGroupQueue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object GroupSyncManager {

    private val db = FirebaseFirestore.getInstance()

    fun syncPendingGroups(
        context: Context,
        lastKnownLocation: Location? = null,
        enrichMissingLocation: Boolean = true,
        onFinished: ((syncedIds: List<String>) -> Unit)? = null
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || !isOnline(context)) {
            onFinished?.invoke(emptyList())
            return
        }

        val offline = OfflineGroupQueue(context)
        val pending = offline.loadPendingGroups()

        if (pending.isEmpty()) {
            GroupMembershipSyncManager.syncPendingMembers(context, onlyGroupIds = null) {
                onFinished?.invoke(emptyList())
            }
            return
        }

        val syncedIds = mutableListOf<String>()
        var left = pending.size

        pending.forEach { g ->
            val payload = sanitizeForRules(
                group = g,
                uid = uid,
                enrichMissingLocation = enrichMissingLocation,
                lastLoc = lastKnownLocation
            )

            db.collection("groups")
                .document(g.id)
                .set(payload)
                .addOnSuccessListener {
                    offline.removeGroup(g.id)
                    syncedIds += g.id
                }
                .addOnFailureListener {}
                .addOnCompleteListener {
                    left--
                    if (left == 0) {
                        val onlyGroupIds = syncedIds.toSet().ifEmpty { null }
                        GroupMembershipSyncManager.syncPendingMembers(context, onlyGroupIds) {
                            onFinished?.invoke(syncedIds)
                        }
                    }
                }
        }
    }

    private fun sanitizeForRules(
        group: Group,
        uid: String,
        enrichMissingLocation: Boolean,
        lastLoc: Location?
    ): Map<String, Any?> {
        val safeName = group.name.trim().take(100).ifBlank { "קבוצה חדשה" }
        val safeDesc = group.description.take(1000)
        val safeStatus = group.status.ifBlank { "planned" }.take(30)

        val enrichedLat = if (group.lat == null && enrichMissingLocation) lastLoc?.latitude else group.lat
        val enrichedLng = if (group.lng == null && enrichMissingLocation) lastLoc?.longitude else group.lng

        val safeLat = enrichedLat?.let { if (it in -90.0..90.0) it else null }
        val safeLng = enrichedLng?.let { if (it in -180.0..180.0) it else null }

        return mapOf(
            "id"        to group.id,
            "name"      to safeName,
            "description" to safeDesc,
            "createdBy" to uid,
            "timestamp" to group.timestamp,
            "status"    to safeStatus,
            "lat"       to safeLat,
            "lng"       to safeLng,
            "isSynced"  to true,
            "isPublic"  to group.isPublic
        )
    }
}