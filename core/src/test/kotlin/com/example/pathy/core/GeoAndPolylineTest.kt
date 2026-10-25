package com.example.pathy.core

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.geo.RoutePolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoAndPolylineTest {

    private val telAviv = LatLon(32.0853, 34.7818)
    private val jerusalem = LatLon(31.7683, 35.2137)

    @Test
    fun distanceMatchesAKnownPair() {
        // Tel Aviv - Jerusalem is about 54 km as the crow flies.
        assertEquals(54_000.0, Geo.distance(telAviv, jerusalem), 1_500.0)
        assertEquals(0.0, Geo.distance(telAviv, telAviv), 1e-6)
    }

    @Test
    fun distanceIsSymmetric() {
        assertEquals(Geo.distance(telAviv, jerusalem), Geo.distance(jerusalem, telAviv), 1e-6)
    }

    @Test
    fun bearingsPointTheRightWay() {
        val o = LatLon(32.0, 35.0)
        assertEquals(0.0, Geo.bearing(o, LatLon(32.1, 35.0)), 0.5)
        assertEquals(90.0, Geo.bearing(o, LatLon(32.0, 35.1)), 0.5)
        assertEquals(180.0, Geo.bearing(o, LatLon(31.9, 35.0)), 0.5)
        assertEquals(270.0, Geo.bearing(o, LatLon(32.0, 34.9)), 0.5)
    }

    @Test
    fun turnBetweenIsSignedAndWrapsAroundNorth() {
        assertEquals(90.0, Geo.turnBetween(0.0, 90.0), 1e-9)      // north -> east is a right turn
        assertEquals(-90.0, Geo.turnBetween(0.0, 270.0), 1e-9)    // north -> west is a left turn
        assertEquals(20.0, Geo.turnBetween(350.0, 10.0), 1e-9)    // across north
        assertEquals(-20.0, Geo.turnBetween(10.0, 350.0), 1e-9)
        assertEquals(180.0, Math.abs(Geo.turnBetween(0.0, 180.0)), 1e-9)
    }

    @Test
    fun destinationRoundTripsWithDistanceAndBearing() {
        val d = Geo.destination(telAviv, 45.0, 1_000.0)
        assertEquals(1_000.0, Geo.distance(telAviv, d), 0.5)
        assertEquals(45.0, Geo.bearing(telAviv, d), 0.5)
    }

    @Test
    fun projectionLandsOnTheNearestSegment() {
        // 1 km east then 1 km north.
        val a = LatLon(32.0, 35.0)
        val b = Geo.destination(a, 90.0, 1_000.0)
        val c = Geo.destination(b, 0.0, 1_000.0)
        val route = RoutePolyline(listOf(a, b, c))
        assertEquals(2_000.0, route.lengthM, 5.0)

        val onFirst = route.project(Geo.destination(Geo.destination(a, 90.0, 400.0), 0.0, 25.0))
        assertEquals(0, onFirst.segment)
        assertEquals(25.0, onFirst.offsetM, 1.0)
        assertEquals(400.0, onFirst.alongM, 2.0)

        val onSecond = route.project(Geo.destination(Geo.destination(b, 0.0, 600.0), 270.0, 10.0))
        assertEquals(1, onSecond.segment)
        assertEquals(1_600.0, onSecond.alongM, 3.0)
    }

    @Test
    fun aSearchWindowKeepsALoopOnTheRightLap() {
        // Out along a line and back along (nearly) the same line: both laps pass the same spot.
        val a = LatLon(32.0, 35.0)
        val b = Geo.destination(a, 90.0, 1_000.0)
        val back = Geo.destination(b, 0.0, 6.0)
        val c = Geo.destination(a, 0.0, 6.0)
        val route = RoutePolyline(listOf(a, b, back, c))
        val spot = Geo.destination(a, 90.0, 500.0)
        val firstLap = route.project(spot, 0, 0)
        val secondLap = route.project(Geo.destination(spot, 0.0, 6.0), 2, 2)
        assertTrue(firstLap.alongM < 600)
        assertTrue(secondLap.alongM > 1_400)
    }

    @Test
    fun pointAtInterpolatesAndClamps() {
        val a = LatLon(32.0, 35.0)
        val b = Geo.destination(a, 90.0, 1_000.0)
        val route = RoutePolyline(listOf(a, b))
        assertEquals(500.0, Geo.distance(a, route.pointAt(500.0)), 1.0)
        assertEquals(a, route.pointAt(-10.0))
        assertEquals(b, route.pointAt(5_000.0))
    }

    @Test
    fun simplificationKeepsCornersAndDropsStraightRuns() {
        val a = LatLon(32.0, 35.0)
        val pts = ArrayList<LatLon>()
        // 100 points along a straight eastward line, then 100 north: one corner.
        for (i in 0..100) pts += Geo.destination(a, 90.0, i * 10.0)
        val corner = pts.last()
        for (i in 1..100) pts += Geo.destination(corner, 0.0, i * 10.0)
        val kept = RoutePolyline.simplifyIndices(pts, 3.0)
        assertEquals(3, kept.size)
        assertEquals(listOf(0, 100, 200), kept)
    }
}
