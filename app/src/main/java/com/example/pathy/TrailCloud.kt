package com.example.pathy

import com.mapbox.geojson.Point
import kotlin.math.*
import android.content.Context
import com.example.pathy.data.SavedTrail
import com.example.pathy.data.TrailData
import com.example.pathy.screens.toPoint
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.util.*

fun uploadTrailToFirestore(
    context: Context,
    trail: SavedTrail,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser

    if (currentUser == null) {
        onFailure(context.getString(R.string.error_not_signed_in))
        return
    }

    db.collection("trails")
        .document(trail.id)
        .set(trail)
        .addOnSuccessListener {

            val linkData = mapOf(
                "trailId" to trail.id,
                "name" to trail.name,
                "linkedAt" to System.currentTimeMillis()
            )

            db.collection("users")
                .document(currentUser.uid)
                .collection("trails_user")
                .document(trail.id)
                .set(linkData)
                .addOnSuccessListener {
                    onSuccess(trail.id)
                }
                .addOnFailureListener { e ->
                    onFailure(context.getString(R.string.trail_link_save_error, e.message))
                }
        }
        .addOnFailureListener { e ->
            onFailure(context.getString(R.string.trail_upload_error, e.message))
        }
}

suspend fun fetchTrailFromFirestore(trailId: String): SavedTrail? {
    return try {
        val snapshot = Firebase.firestore.collection("trails")
            .document(trailId)
            .get()
            .await()

        snapshot.toObject(SavedTrail::class.java)
    } catch (e: Exception) {
        android.util.Log.w("PATHY", "Operation failed", e)
        null
    }
}

/**
 * Community trails within [maxDistanceMeters] of [center]. The query asks Firestore only for the band of
 * latitudes around the point (one range filter, no extra index) and keeps the longitude and distance
 * check on the phone. The old version downloaded the whole collection at every call, which costs a read
 * per trail and grows with every trail anyone publishes.
 */
suspend fun fetchFirestoreTrailsNearby(center: Point, maxDistanceMeters: Double = 5000.0): List<TrailData> {
    val dLat = maxDistanceMeters / 111_320.0
    val snapshot = FirebaseFirestore.getInstance().collection("trails")
        .whereGreaterThanOrEqualTo("midpoint.latitude", center.latitude() - dLat)
        .whereLessThanOrEqualTo("midpoint.latitude", center.latitude() + dLat)
        .limit(300)
        .get()
        .await()
    return snapshot.documents.mapNotNull { it.toObject(TrailData::class.java) }
        .filter { trail ->
            trail.midpoint != null &&
                    trail.midpoint.toPoint().distanceTo(center) <= maxDistanceMeters
        }
}

fun areTrailsDuplicates(t1: TrailData, t2: TrailData): Boolean {
    return t1.name == t2.name &&
            t1.distanceInMeters == t2.distanceInMeters &&
            t1.durationInMillis == t2.durationInMillis &&
            t1.midpoint?.latitude == t2.midpoint?.latitude &&
            t1.midpoint?.longitude == t2.midpoint?.longitude
}

fun mergeTrailsUnique(baseList: List<TrailData>, extraList: List<TrailData>): List<TrailData> {
    val result = mutableListOf<TrailData>()
    result.addAll(baseList)

    for (trail in extraList) {
        if (result.none { areTrailsDuplicates(it, trail) }) {
            result.add(trail)
        }
    }

    return result
}
