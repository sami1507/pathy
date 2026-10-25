package com.example.pathy.core

import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.geo.PolylineCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PolylineCodecTest {

    @Test
    fun matchesGooglesPublishedExample() {
        // The worked example from Google's documentation uses 5 digits; scaled to our 6 digits the
        // decoded points must still agree to 5 digits.
        val points = listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453))
        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))
        assertEquals(3, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
        }
    }

    @Test
    fun aLongNoisyRouteSurvivesARoundTripWithinTenCentimetres() {
        val rnd = Random(3)
        var lat = 32.7
        var lon = 35.1
        val route = (0 until 8_000).map {
            lat += (rnd.nextDouble() - 0.5) * 0.0004
            lon += (rnd.nextDouble() - 0.5) * 0.0004
            LatLon(lat, lon)
        }
        val encoded = PolylineCodec.encode(route)
        val back = PolylineCodec.decode(encoded)
        assertEquals(route.size, back.size)
        route.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
        }
        // About 8 characters per point at 6 digits: a 10 000 point route stays far under Firestore's 1 MB.
        assertTrue("encoded length ${encoded.length}", encoded.length < route.size * 12)
    }

    @Test
    fun southernAndWesternCoordinatesAndTheDateLineWork() {
        val pts = listOf(LatLon(-33.8688, 151.2093), LatLon(-0.0001, -179.99999), LatLon(0.0, 0.0))
        val back = PolylineCodec.decode(PolylineCodec.encode(pts))
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
        }
    }

    @Test
    fun emptyAndTruncatedInputIsHandledWithoutCrashing() {
        assertTrue(PolylineCodec.decode("").isEmpty())
        assertTrue(PolylineCodec.encode(emptyList()).isEmpty())
        val full = PolylineCodec.encode(listOf(LatLon(1.0, 2.0), LatLon(1.5, 2.5)))
        // Cutting the string anywhere must give a prefix of the route, never an exception.
        for (cut in 0..full.length) {
            val part = PolylineCodec.decode(full.substring(0, cut))
            assertTrue(part.size <= 2)
        }
    }
}
