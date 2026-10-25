package com.example.pathy.history

import com.example.pathy.tracking.ActivityDocument
import com.example.pathy.tracking.LoadedRecording
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * The cloud history of recorded outings: save, list, open, delete and total up.
 *
 * Recording itself happens on the phone (see RecordingService). An outing reaches this class only
 * when the upload worker has a connection, and it is written as a single document, see
 * [ActivityDocument].
 */
class ActivityHistoryManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** Stores a recording. The recording id is the document id, so saving twice is harmless. */
    suspend fun saveRecording(userId: String, loaded: LoadedRecording): Result<String> = attempt {
        val data = ActivityDocument.toMap(userId, loaded, createdAtMs = System.currentTimeMillis())
        firestore.collection("activities").document(loaded.recording.id).set(data).await()
        loaded.recording.id
    }

    suspend fun getActivityHistory(userId: String, limit: Int = 50): Result<List<ActivitySummary>> = attempt {
        val snapshot = firestore.collection("activities")
            .whereEqualTo("userId", userId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            runCatching {
                ActivitySummary(
                    id = doc.id,
                    activityType = doc.getString("activityType") ?: "HIKING",
                    startTime = doc.getLong("startTime") ?: 0,
                    duration = doc.getLong("duration") ?: 0,
                    distance = doc.getDouble("distance")?.toFloat() ?: 0f,
                    elevationGain = doc.getDouble("elevationGain")?.toFloat() ?: 0f,
                    calories = doc.getLong("calories")?.toInt() ?: 0,
                    photos = (doc.get("photos") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    notes = doc.getString("notes") ?: ""
                )
            }.getOrNull()
        }
    }

    /** Opens one outing. Version 2 documents carry their route; older ones keep it in a subcollection. */
    suspend fun getActivityDetails(activityId: String): Result<ActivityDetails> = attempt {
        val doc = firestore.collection("activities").document(activityId).get().await()
        check(doc.exists()) { "Activity unavailable" }
        val data = doc.data.orEmpty()

        val route = ActivityDocument.routeFrom(data) ?: legacyRoute(activityId)
        ActivityDetails(
            id = doc.id,
            activityType = doc.getString("activityType") ?: "HIKING",
            startTime = doc.getLong("startTime") ?: 0,
            endTime = doc.getLong("endTime") ?: 0,
            duration = doc.getLong("duration") ?: 0,
            distance = doc.getDouble("distance")?.toFloat() ?: 0f,
            elevationGain = doc.getDouble("elevationGain")?.toFloat() ?: 0f,
            averageSpeed = doc.getDouble("averageSpeed")?.toFloat() ?: 0f,
            maxSpeed = doc.getDouble("maxSpeed")?.toFloat() ?: 0f,
            calories = doc.getLong("calories")?.toInt() ?: 0,
            routePoints = route,
            photos = (doc.get("photos") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            notes = doc.getString("notes") ?: "",
            title = doc.getString("title").orEmpty()
        )
    }

    private suspend fun legacyRoute(activityId: String): List<RoutePoint> {
        val snapshot = firestore.collection("activities").document(activityId).collection("route").get().await()
        return snapshot.documents.mapNotNull { d ->
            RoutePoint(
                lat = d.getDouble("lat") ?: return@mapNotNull null,
                lon = d.getDouble("lon") ?: return@mapNotNull null,
                altitude = d.getDouble("altitude") ?: 0.0,
                time = d.getLong("time") ?: 0
            )
        }.sortedBy { it.time }
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> = attempt {
        // Older outings keep one document per point; remove them in batches of 400.
        val route = firestore.collection("activities").document(activityId).collection("route").get().await()
        route.documents.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        firestore.collection("activities").document(activityId).delete().await()
    }

    suspend fun getStatistics(userId: String, startDate: Long, endDate: Long): Result<PeriodStatistics> = attempt {
        val snapshot = firestore.collection("activities")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("startTime", startDate)
            .whereLessThanOrEqualTo("startTime", endDate)
            .get()
            .await()
        var distance = 0f
        var duration = 0L
        var elevation = 0f
        var calories = 0
        snapshot.documents.forEach { doc ->
            distance += doc.getDouble("distance")?.toFloat() ?: 0f
            duration += doc.getLong("duration") ?: 0
            elevation += doc.getDouble("elevationGain")?.toFloat() ?: 0f
            calories += doc.getLong("calories")?.toInt() ?: 0
        }
        val count = snapshot.size()
        PeriodStatistics(
            activityCount = count,
            totalDistance = distance,
            totalDuration = duration,
            totalElevationGain = elevation,
            totalCalories = calories,
            averageDistance = if (count > 0) distance / count else 0f,
            averageDuration = if (count > 0) duration / count else 0
        )
    }

    /** Runs [block] on the IO dispatcher and returns its value or the failure, never throwing (but never hiding a cancel). */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ActivitySummary(
    val id: String,
    val activityType: String,
    val startTime: Long,
    val duration: Long,
    val distance: Float,
    val elevationGain: Float,
    val calories: Int,
    val photos: List<String>,
    val notes: String,
    /** True for an outing that is saved on the phone and has not reached the cloud yet. */
    val pendingUpload: Boolean = false
)

data class ActivityDetails(
    val id: String,
    val activityType: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val distance: Float,
    val elevationGain: Float,
    val averageSpeed: Float,
    val maxSpeed: Float,
    val calories: Int,
    val routePoints: List<RoutePoint>,
    val photos: List<String>,
    val notes: String,
    val title: String = ""
)

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val time: Long
)

data class PeriodStatistics(
    val activityCount: Int,
    val totalDistance: Float,
    val totalDuration: Long,
    val totalElevationGain: Float,
    val totalCalories: Int,
    val averageDistance: Float,
    val averageDuration: Long
)
