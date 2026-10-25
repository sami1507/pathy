package com.example.pathy.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

object FirebaseRepository {

    suspend fun getUserTrailIds(uid: String, propagateFailure: Boolean = false): List<String> {
        val db = Firebase.firestore
        return try {
            val snapshot = db.collection("users")
                .document(uid)
                .collection("trails_user")
                .get()
                .await()

            snapshot.documents.mapNotNull { it.id }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (propagateFailure) throw e
            emptyList()
        }
    }

    suspend fun getTrailsByIds(trailIds: List<String>): List<SavedTrail> {
        val db = Firebase.firestore
        val trails = mutableListOf<SavedTrail>()

        for (id in trailIds) {
            val snapshot = db.collection("trails").document(id).get().await()
            snapshot.toObject(SavedTrail::class.java)?.let {
                trails.add(it)
            }
        }

        return trails
    }
}