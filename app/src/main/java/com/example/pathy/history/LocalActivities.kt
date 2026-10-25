package com.example.pathy.history

import com.example.pathy.tracking.LoadedRecording
import com.example.pathy.tracking.RecordingEntity

/** Outings recorded on this phone, shown with the same types as the ones from the cloud history. */
object LocalActivities {

    fun summary(r: RecordingEntity) = ActivitySummary(
        id = r.id,
        activityType = r.sport,
        startTime = r.startedAtMs,
        duration = r.elapsedMs,
        distance = r.distanceM.toFloat(),
        elevationGain = r.elevationGainM.toFloat(),
        calories = r.calories,
        photos = emptyList(),
        notes = "",
        pendingUpload = !r.uploaded
    )

    fun details(loaded: LoadedRecording): ActivityDetails {
        val r = loaded.recording
        return ActivityDetails(
            id = r.id,
            activityType = r.sport,
            startTime = r.startedAtMs,
            endTime = r.finishedAtMs,
            duration = r.elapsedMs,
            distance = r.distanceM.toFloat(),
            elevationGain = r.elevationGainM.toFloat(),
            averageSpeed = r.averageSpeedKmh.toFloat(),
            maxSpeed = r.maxSpeedKmh.toFloat(),
            calories = r.calories,
            routePoints = loaded.points.map { RoutePoint(it.lat, it.lon, it.altitudeM ?: 0.0, it.timeMs) },
            photos = emptyList(),
            notes = "",
            title = r.title
        )
    }
}
