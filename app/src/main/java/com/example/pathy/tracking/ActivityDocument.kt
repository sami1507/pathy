package com.example.pathy.tracking

import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.geo.PolylineCodec
import com.example.pathy.core.geo.RoutePolyline
import com.example.pathy.core.track.TrackPoint
import com.example.pathy.history.RoutePoint

/**
 * How an outing is stored in the cloud history. The first version wrote one Firestore document per
 * GPS point (thousands of writes and reads per outing, and a request that never finished without a
 * connection). Version 2 keeps the whole route inside the activity document as an encoded line plus
 * two small arrays, so saving is one write and opening is one read.
 */
object ActivityDocument {
    const val SCHEMA = 2

    /** Firestore documents are limited to 1 MiB; this keeps a long outing well inside that. */
    const val MAX_STORED_POINTS = 12_000

    fun toMap(userId: String, loaded: LoadedRecording, createdAtMs: Long): Map<String, Any?> {
        val r = loaded.recording
        val kept = downsample(loaded.points)
        val first = loaded.points.firstOrNull()
        val last = loaded.points.lastOrNull()
        return mapOf(
            "userId" to userId,
            "activityId" to r.id,
            "activityType" to r.sport,
            "startTime" to r.startedAtMs,
            "endTime" to r.finishedAtMs,
            "duration" to r.elapsedMs,
            "movingTime" to r.movingMs,
            "distance" to r.distanceM,
            "elevationGain" to r.elevationGainM,
            "elevationLoss" to r.elevationLossM,
            "averageSpeed" to r.averageSpeedKmh,
            "maxSpeed" to r.maxSpeedKmh,
            "calories" to r.calories,
            "startLocation" to first?.let { mapOf("lat" to it.lat, "lon" to it.lon) },
            "endLocation" to last?.let { mapOf("lat" to it.lat, "lon" to it.lon) },
            "title" to r.title,
            "photos" to emptyList<String>(),
            "notes" to "",
            "createdAt" to createdAtMs,
            // The route
            "schema" to SCHEMA,
            "routeEncoded" to PolylineCodec.encode(kept.map { LatLon(it.lat, it.lon) }),
            // Altitude in decimetres and time as seconds from the start: whole numbers are far smaller
            // than doubles and the precision is more than a phone can measure.
            "altitudesDm" to kept.map { altitudeDm(it.altitudeM) },
            "timesSec" to kept.map { ((it.timeMs - r.startedAtMs) / 1000L).coerceAtLeast(0) },
            "pointCount" to loaded.points.size
        )
    }

    /** Reads the route of a version 2 document, or null when the document is the old kind. */
    fun routeFrom(data: Map<String, Any?>): List<RoutePoint>? {
        val encoded = data["routeEncoded"] as? String ?: return null
        val line = PolylineCodec.decode(encoded)
        val altitudes = (data["altitudesDm"] as? List<*>)?.map { (it as? Number)?.toLong() } ?: emptyList()
        val times = (data["timesSec"] as? List<*>)?.map { (it as? Number)?.toLong() } ?: emptyList()
        val start = (data["startTime"] as? Number)?.toLong() ?: 0L
        return line.mapIndexed { i, p ->
            val dm = altitudes.getOrNull(i)
            RoutePoint(
                lat = p.lat,
                lon = p.lon,
                altitude = if (dm == null || dm == ALTITUDE_UNKNOWN) 0.0 else dm / 10.0,
                time = start + (times.getOrNull(i) ?: 0L) * 1000L
            )
        }
    }

    /** Points of a stored route as the core track type, for export. */
    fun trackPoints(route: List<RoutePoint>): List<TrackPoint> =
        route.map { TrackPoint(it.lat, it.lon, it.altitude.takeIf { a -> a != 0.0 }, it.time, null) }

    /**
     * Thins a very long route to [MAX_STORED_POINTS] while keeping its shape (Douglas-Peucker with a
     * growing tolerance). Short routes are stored untouched.
     */
    fun downsample(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size <= MAX_STORED_POINTS) return points
        val line = points.map { LatLon(it.lat, it.lon) }
        var tolerance = 1.0
        while (tolerance < 200.0) {
            val idx = RoutePolyline.simplifyIndices(line, tolerance)
            if (idx.size <= MAX_STORED_POINTS) return idx.map { points[it] }
            tolerance *= 1.6
        }
        // Pathological input: fall back to even sampling.
        val step = points.size / MAX_STORED_POINTS + 1
        return points.filterIndexed { i, _ -> i % step == 0 || i == points.lastIndex }
    }

    private const val ALTITUDE_UNKNOWN = Long.MIN_VALUE

    private fun altitudeDm(altitudeM: Double?): Long =
        if (altitudeM == null) ALTITUDE_UNKNOWN else Math.round(altitudeM * 10)
}
