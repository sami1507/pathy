package com.example.pathy.data

import android.util.Log
import com.example.pathy.CommunityEvent
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage

object EventRepository {

    fun uploadEventToFirestore(
        event: CommunityEvent,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val eventRef = firestore.collection("events").document(event.id)

        eventRef.set(event)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }


    fun deleteEventWithImages(eventId: String, imageUrls: List<String>) {
        val db = Firebase.firestore
        val storage = FirebaseStorage.getInstance()

        for (url in imageUrls) {
            val storageRef = storage.getReferenceFromUrl(url)
            storageRef.delete()
                .addOnSuccessListener {
                    Log.d("DELETE_EVENT", "תמונה נמחקה בהצלחה: $url")
                }
                .addOnFailureListener {
                    Log.e("DELETE_EVENT", "שגיאה במחיקת תמונה: $url", it)
                }
        }

        db.collection("events").document(eventId).collection("comments")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().addOnSuccessListener {
                    Log.d("DELETE_EVENT", "תגובות נמחקו")
                }.addOnFailureListener {
                    Log.e("DELETE_EVENT", "שגיאה במחיקת תגובות", it)
                }
            }
            .addOnFailureListener {
                Log.e("DELETE_EVENT", "שגיאה בשליפת תגובות", it)
            }

        db.collection("events").document(eventId)
            .delete()
    }


    fun registerToEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        val eventRef = db.collection("events").document(eventId)

        eventRef.update("attendees", FieldValue.arrayUnion(userId))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun unregisterFromEvent(eventId: String, userId: String, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        val eventRef = db.collection("events").document(eventId)

        eventRef.update("attendees", FieldValue.arrayRemove(userId))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}
