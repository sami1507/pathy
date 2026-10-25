package com.example.pathy

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.pathy.data.Group
import com.example.pathy.data.OfflineGroupQueue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateGroupViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    fun saveGroup(
        group: Group,
        context: Context,
        onSaved: (online: Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

        if (uid == null) {
            OfflineGroupQueue(context).saveGroupLocally(group.copy(createdBy = ""))
            onSaved(false)
            return
        }

        val payload = hashMapOf<String, Any?>(
            "id" to group.id,
            "name" to group.name.trim(),
            "description" to group.description.take(1000),
            "createdBy" to uid,
            "timestamp" to group.timestamp,
            "status" to group.status.ifBlank { "planned" }.take(30),
            "lat" to group.lat,
            "lng" to group.lng,
            "isSynced" to true,
            "isPublic" to group.isPublic
        )

        if (isOnline(context)) {
            db.collection("groups")
                .document(group.id)
                .set(payload)
                .addOnSuccessListener { onSaved(true) }
                .addOnFailureListener { e ->
                    OfflineGroupQueue(context).saveGroupLocally(
                        group.copy(createdBy = uid, isSynced = false)
                    )
                    onSaved(false)
                }
        } else {
            OfflineGroupQueue(context).saveGroupLocally(
                group.copy(createdBy = uid, isSynced = false)
            )
            onSaved(false)
        }
    }

    fun fetchGroups(onResult: (List<Group>) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val results = mutableListOf<Group>()

        db.collection("groups")
            .whereEqualTo("isPublic", true)
            .get()
            .addOnSuccessListener { publicSnap ->
                results += publicSnap.mapNotNull { it.toObject(Group::class.java) }

                if (uid != null) {
                    db.collection("groups")
                        .whereEqualTo("createdBy", uid)
                        .get()
                        .addOnSuccessListener { mineSnap ->
                            results += mineSnap.mapNotNull { it.toObject(Group::class.java) }
                            onResult(results.distinctBy { it.id })
                        }
                        .addOnFailureListener {
                            onResult(results.distinctBy { it.id })
                        }
                } else {
                    onResult(results.distinctBy { it.id })
                }
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun joinGroup(groupId: String, userId: String, userName: String, onResult: (Boolean) -> Unit) {
        val memberRef = db.collection("groups")
            .document(groupId)
            .collection("members")
            .document(userId)

        memberRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onResult(false)
                } else {
                    val memberData = hashMapOf(
                        "uid" to userId,
                        "name" to userName,
                        "joinedAt" to System.currentTimeMillis()
                    )
                    memberRef.set(memberData)
                        .addOnSuccessListener { onResult(true) }
                        .addOnFailureListener { onResult(false) }
                }
            }
            .addOnFailureListener { onResult(false) }
    }
}