package com.example.pathy.data

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.gpx.Gpx
import com.example.pathy.core.track.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GpxImportTest {

    private val start = LatLon(32.0, 35.0)

    private fun walk(lengthM: Double, step: Double = 10.0, altitude: ((Int) -> Double?)? = null): List<TrackPoint> {
        val pts = ArrayList<TrackPoint>()
        var p = start
        var i = 0
        var left = lengthM
        pts += TrackPoint(p.lat, p.lon, altitude?.invoke(0), 0L, null)
        while (left > 0) {
            p = Geo.destination(p, 60.0, minOf(step, left))
            left -= step
            i++
            pts += TrackPoint(p.lat, p.lon, altitude?.invoke(i), 0L, null)
        }
        return pts
    }

    private fun roundTrip(points: List<TrackPoint>, name: String = "Test route") =
        GpxImport.read(Gpx.write(name, points).byteInputStream())

    @Test
    fun aRecordedRouteBecomesATrailWithItsRealClimb() {
        // 3 km walk that climbs 150 m steadily.
        val points = walk(3_000.0) { 100.0 + it * 0.5 }
        val trail = GpxImport.toTrailData(roundTrip(points, "Mount Test"))
        assertEquals("Mount Test", trail.name)
        assertEquals(3_000.0, trail.distanceInMeters, 30.0)
        assertEquals(150.0, trail.tags.elevationGain!!.toDouble(), 15.0)
        assertTrue("maximum altitude is kept", trail.tags.maxElevation!! > 230)
        assertTrue(trail.isHikingTrail)
        // 3 km at 4 km/h plus the climb is a bit over an hour.
        assertTrue(trail.durationInMillis in 60 * 60_000L..90 * 60_000L)
    }

    @Test
    fun withoutAltitudesTheClimbStaysUnknown() {
        val trail = GpxImport.toTrailData(roundTrip(walk(2_000.0)))
        assertNull(trail.tags.elevationGain)
        assertNull(trail.tags.maxElevation)
        assertEquals(DifficultyLevel.EASY, trail.difficulty)
    }

    @Test
    fun aHugeRouteIsThinnedToFitBetweenScreens() {
        val trail = GpxImport.toTrailData(roundTrip(walk(30_000.0, step = 2.0)))
        assertTrue(trail.geoPoints.size <= 1_500)
        assertEquals(30_000.0, trail.distanceInMeters, 300.0)
    }

    @Test
    fun theSameFileGivesTheSameTrailId() {
        val a = GpxImport.toTrailData(roundTrip(walk(1_500.0)))
        val b = GpxImport.toTrailData(roundTrip(walk(1_500.0)))
        val other = GpxImport.toTrailData(roundTrip(walk(1_800.0)))
        assertEquals(a.id, b.id)
        assertNotEquals(a.id, other.id)
    }

    @Test
    fun tooShortOrBrokenFilesAreRefusedWithAClearError() {
        try {
            GpxImport.toTrailData(roundTrip(walk(40.0, step = 10.0)))
            fail("a 40 m route is not a trail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("short"))
        }
        try {
            GpxImport.read("this is not xml at all".byteInputStream())
            fail("garbage must be rejected")
        } catch (expected: IllegalArgumentException) {
            // good: one exception type for the screen to handle
        }
    }

    @Test
    fun aFileFarLargerThanAnyRouteIsRefusedWithoutReadingItAll() {
        val endless = object : java.io.InputStream() {
            var served = 0L
            override fun read(): Int { served++; return 'a'.code }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; java.util.Arrays.fill(b, off, off + len, 'a'.code.toByte()); return len }
        }
        try {
            GpxImport.read(endless)
            fail("should stop at the size limit")
        } catch (expected: java.io.IOException) {
            assertTrue("stopped soon after the limit", endless.served < GpxImport.MAX_BYTES * 2L)
        }
    }
}
