package com.example.pathy

import android.content.Context
import android.util.Log
import com.example.pathy.data.OfflineMembershipQueue
import com.google.firebase.firestore.FirebaseFirestore

object GroupMembershipSyncManager {

    private val db = FirebaseFirestore.getInstance()


    fun syncPendingMembers(
        context: Context,
        onlyGroupIds: Set<String>? = null,
        onDone: (() -> Unit)? = null
    ) {
        val queue = OfflineMembershipQueue(context)
        val pendingAll = queue.load()
        val pending = if (onlyGroupIds == null) pendingAll else pendingAll.filter { it.groupId in onlyGroupIds }

        if (pending.isEmpty()) { onDone?.invoke(); return }

        var left = pending.size
        pending.forEach { m ->
            db.collection("groups")
                .document(m.groupId)
                .collection("members")
                .document(m.uid)
                .set(mapOf("uid" to m.uid, "name" to m.name, "joinedAt" to m.joinedAt))
                .addOnSuccessListener { queue.remove(m.groupId, m.uid) }
                .addOnFailureListener { e ->
                    Log.e("GroupMembershipSync", "failed to sync member ${m.uid} of ${m.groupId}", e)
                }
                .addOnCompleteListener {
                    left--
                    if (left == 0) onDone?.invoke()
                }
        }
    }
}
