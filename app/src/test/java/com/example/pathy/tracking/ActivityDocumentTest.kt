package com.example.pathy.tracking

import com.example.pathy.core.track.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDocumentTest {

    private val recording = RecordingEntity(
        id = "r1", sport = "HIKING", startedAtMs = 1_700_000_000_000L, status = RecordingEntity.STATUS_FINISHED,
        finishedAtMs = 1_700_000_600_000L, distanceM = 1234.5, elapsedMs = 600_000, movingMs = 550_000,
        elevationGainM = 42.0, elevationLossM = 10.0, averageSpeedKmh = 8.1, maxSpeedKmh = 9.5, calories = 321
    )

    private fun point(i: Int, altitude: Double? = 100.0 + i) = TrackPoint(
        lat = 32.0 + i * 0.0001, lon = 35.0 + i * 0.00005, altitudeM = altitude,
        timeMs = recording.startedAtMs + i * 5_000L, accuracyM = 6f
    )

    @Test
    fun theRouteSurvivesTheRoundTripThroughTheDocument() {
        val points = (0 until 50).map { point(it) }
        val map = ActivityDocument.toMap("user1", LoadedRecording(recording, points), createdAtMs = 5L)
        val route = ActivityDocument.routeFrom(map)!!

        assertEquals(50, route.size)
        route.zip(points).forEach { (r, p) ->
            assertEquals(p.lat, r.lat, 1e-5)
            assertEquals(p.lon, r.lon, 1e-5)
            assertEquals(p.altitudeM!!, r.altitude, 0.1)
            assertEquals(p.timeMs, r.time)
        }
    }

    @Test
    fun theSummaryFieldsMatchWhatTheHistoryScreenReads() {
        val map = ActivityDocument.toMap("user1", LoadedRecording(recording, listOf(point(0), point(1))), createdAtMs = 5L)
        assertEquals("user1", map["userId"])
        assertEquals("HIKING", map["activityType"])
        assertEquals(recording.startedAtMs, map["startTime"])
        assertEquals(600_000L, map["duration"])
        assertEquals(1234.5, map["distance"])
        assertEquals(42.0, map["elevationGain"])
        assertEquals(321, map["calories"])
        assertEquals(ActivityDocument.SCHEMA, map["schema"])
    }

    @Test
    fun anUnknownAltitudeStaysUnknownAndIsNotStoredAsSeaLevel() {
        val points = listOf(point(0, altitude = null), point(1, altitude = 250.0))
        val map = ActivityDocument.toMap("u", LoadedRecording(recording, points), createdAtMs = 1L)
        val route = ActivityDocument.routeFrom(map)!!
        assertEquals(0.0, route[0].altitude, 0.0)
        assertEquals(250.0, route[1].altitude, 0.1)
        // Exported as a track, the unknown altitude is left out rather than written as 0 m.
        assertNull(ActivityDocument.trackPoints(route)[0].altitudeM)
        assertNotNull(ActivityDocument.trackPoints(route)[1].altitudeM)
    }

    @Test
    fun anOldDocumentWithoutAnEncodedRouteIsRecognised() {
        assertNull(ActivityDocument.routeFrom(mapOf("userId" to "u", "startTime" to 1L)))
    }

    @Test
    fun aVeryLongOutingIsThinnedToFitOneDocumentButKeepsItsEnds() {
        val n = ActivityDocument.MAX_STORED_POINTS * 3
        // A gently curving line, so thinning has something to simplify.
        val points = (0 until n).map { i ->
            TrackPoint(32.0 + i * 0.00001, 35.0 + Math.sin(i / 200.0) * 0.0005, 100.0, recording.startedAtMs + i * 1_000L, 5f)
        }
        val kept = ActivityDocument.downsample(points)
        assertTrue(kept.size <= ActivityDocument.MAX_STORED_POINTS)
        assertEquals(points.first(), kept.first())
        assertEquals(points.last(), kept.last())
        assertTrue("order is preserved", kept.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs })
    }
}
