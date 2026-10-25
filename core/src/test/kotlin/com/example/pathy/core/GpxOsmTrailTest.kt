package com.example.pathy.core

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.gpx.Gpx
import com.example.pathy.core.osm.OverpassParser
import com.example.pathy.core.track.TrackPoint
import com.example.pathy.core.trail.ElevationProfile
import com.example.pathy.core.trail.Grade
import com.example.pathy.core.trail.TrailMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GpxOsmTrailTest {

    // ---- GPX ----------------------------------------------------------------------------------

    @Test
    fun gpxRoundTripKeepsPositionsAltitudeAndTime() {
        val points = listOf(
            TrackPoint(32.123456, 35.654321, 812.5, 1_700_000_000_000L, null),
            TrackPoint(32.123556, 35.654421, 815.0, 1_700_000_005_000L, null),
            TrackPoint(32.123656, 35.654521, null, 0L, null)
        )
        val xml = Gpx.write("Wadi & <Test> \"Trail\"", points)
        val parsed = Gpx.parse(xml.byteInputStream())
        assertEquals("Wadi & <Test> \"Trail\"", parsed.name)
        assertEquals(3, parsed.points.size)
        assertEquals(32.123456, parsed.points[0].lat, 1e-6)
        assertEquals(812.5, parsed.points[0].altitudeM!!, 0.05)
        assertEquals(1_700_000_000_000L, parsed.points[0].timeMs)
        assertNull(parsed.points[2].altitudeM)
    }

    @Test
    fun gpxFromOtherAppsWithFractionsAndOffsetsIsRead() {
        val xml = """
            <?xml version="1.0"?>
            <gpx version="1.1" creator="Other" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Meta name</name></metadata>
              <wpt lat="32.0" lon="35.0"><name>A waypoint</name></wpt>
              <trk><name>Carmel loop</name><trkseg>
                <trkpt lat="32.7" lon="35.0"><ele>100</ele><time>2024-05-01T10:20:30.123Z</time></trkpt>
                <trkpt lat="32.8" lon="35.1"><ele>110.5</ele><time>2024-05-01T12:20:30+02:00</time></trkpt>
              </trkseg></trk>
            </gpx>""".trimIndent()
        val t = Gpx.parse(xml.byteInputStream())
        assertEquals("Carmel loop", t.name)
        assertEquals(2, t.points.size)
        assertEquals(1_714_558_830_123L, t.points[0].timeMs)
        // The second fix is the same instant as 10:20:30Z written with a +02:00 offset.
        assertEquals(1_714_558_830_000L, t.points[1].timeMs)
    }

    @Test
    fun aRouteOnlyGpxIsReadAsATrack() {
        val xml = """<gpx><rte><name>R</name><rtept lat="1" lon="2"/><rtept lat="1.1" lon="2.1"/></rte></gpx>"""
        val t = Gpx.parse(xml.byteInputStream())
        assertEquals("R", t.name)
        assertEquals(2, t.points.size)
    }

    @Test
    fun gpxWithoutCoordinatesOrWithBadOnesIsRejectedCleanly() {
        try {
            Gpx.parse("<gpx><trk><trkseg></trkseg></trk></gpx>".byteInputStream())
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        val t = Gpx.parse("""<gpx><trk><trkseg><trkpt lat="999" lon="2"/><trkpt lat="3" lon="4"/></trkseg></trk></gpx>""".byteInputStream())
        assertEquals(1, t.points.size) // the impossible latitude was dropped
    }

    @Test
    fun externalEntitiesInGpxAreNotResolved() {
        val xml = """<?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <gpx><trk><name>&secret;</name><trkseg><trkpt lat="1" lon="2"/></trkseg></trk></gpx>"""
        val name = try { Gpx.parse(xml.byteInputStream()).name } catch (_: Exception) { "" }
        assertFalse(name.contains("root:"))
    }

    // ---- Overpass relations ---------------------------------------------------------------------

    private fun geom(vararg p: Pair<Double, Double>) =
        p.joinToString(",", "[", "]") { """{"lat":${it.first},"lon":${it.second}}""" }

    @Test
    fun aRelationBecomesOneNamedRouteEvenWhenItsWaysAreOutOfOrderAndReversed() {
        // Three ways that form A -> B -> C -> D, listed as: second, first (reversed), third.
        val json = """{"elements":[
          {"type":"relation","id":77,"tags":{"route":"hiking","name":"Israel National Trail","name:he":"שביל ישראל","ref":"INT","sac_scale":"mountain_hiking"},
           "members":[
             {"type":"way","ref":2,"role":"","geometry":${geom(32.001 to 35.0, 32.002 to 35.0)}},
             {"type":"way","ref":1,"role":"","geometry":${geom(32.001 to 35.0, 32.000 to 35.0)}},
             {"type":"way","ref":3,"role":"","geometry":${geom(32.002 to 35.0, 32.003 to 35.0)}},
             {"type":"node","ref":9,"role":"guidepost","lat":32.0,"lon":35.0}
           ]}
        ]}"""
        val trails = OverpassParser.parse(json)
        assertEquals(1, trails.size)
        val t = trails.single()
        assertTrue(t.isRelation)
        assertEquals("Israel National Trail", t.displayName())
        assertEquals("שביל ישראל", t.displayName("he"))
        assertEquals("Israel National Trail", t.displayName("ar")) // no Arabic name: falls back
        assertEquals(4, t.path.size)
        assertTrue(t.extraSegments.isEmpty())
        // One single line from 32.000 to 32.003, ~333 m, in order.
        assertEquals(333.0, t.lengthM, 8.0)
        assertTrue(t.path.first().lat < t.path.last().lat || t.path.first().lat > t.path.last().lat)
        for (i in 1 until t.path.size) assertEquals(111.0, Geo.distance(t.path[i - 1], t.path[i]), 3.0)
    }

    @Test
    fun disjointPiecesBecomeExtraSegmentsNotOneBrokenLine() {
        val json = """{"elements":[{"type":"relation","id":5,"tags":{"route":"hiking","name":"Gap"},
          "members":[
            {"type":"way","ref":1,"geometry":${geom(32.0 to 35.0, 32.01 to 35.0)}},
            {"type":"way","ref":2,"geometry":${geom(33.0 to 36.0, 33.002 to 36.0)}}
          ]}]}"""
        val t = OverpassParser.parse(json).single()
        assertEquals(1, t.extraSegments.size)
        assertTrue(t.lengthM > 1_000.0) // the longer piece is the main line
    }

    @Test
    fun plainWaysAndBrokenElementsAreHandled() {
        val json = """{"elements":[
          {"type":"way","id":10,"tags":{"highway":"path","name":"Short"},"geometry":${geom(1.0 to 1.0, 1.001 to 1.0)}},
          {"type":"way","id":11,"tags":{},"geometry":[]},
          {"type":"relation","id":12,"tags":{"route":"hiking"},"members":[]},
          {"type":"node","id":13,"lat":1,"lon":1}
        ]}"""
        val trails = OverpassParser.parse(json)
        assertEquals(1, trails.size)
        assertFalse(trails.single().isRelation)
        assertEquals("osm_w10", trails.single().id)
        assertEquals("Short", trails.single().displayName())
    }

    @Test
    fun anEmptyAnswerIsNotAnError() {
        assertTrue(OverpassParser.parse("""{"elements":[]}""").isEmpty())
        assertTrue(OverpassParser.parse("""{"version":0.6}""").isEmpty())
    }

    // ---- grades, durations, elevation -------------------------------------------------------------

    @Test
    fun hikingGradeUsesTheSacScaleAndEffort() {
        assertEquals(Grade.EASY, TrailMetrics.hikingGrade(4_000.0, 60.0, null))
        assertEquals(Grade.MODERATE, TrailMetrics.hikingGrade(5_000.0, 150.0, null))
        assertEquals(Grade.HARD, TrailMetrics.hikingGrade(10_000.0, 500.0, null))
        assertEquals(Grade.EXTREME, TrailMetrics.hikingGrade(20_000.0, 1_000.0, null))
        // A short walk marked demanding_mountain_hiking is hard whatever its length.
        assertEquals(Grade.HARD, TrailMetrics.hikingGrade(2_000.0, 20.0, "demanding_mountain_hiking"))
        assertEquals(Grade.EXTREME, TrailMetrics.hikingGrade(2_000.0, 20.0, "alpine_hiking"))
    }

    @Test
    fun anUnknownClimbIsNotInventedAndNeverMakesATrailExtreme() {
        assertEquals(Grade.EASY, TrailMetrics.hikingGrade(3_000.0, null, null))
        assertEquals(Grade.MODERATE, TrailMetrics.hikingGrade(9_000.0, null, null))
        assertEquals(Grade.HARD, TrailMetrics.hikingGrade(40_000.0, null, null))
    }

    @Test
    fun otherSportGrades() {
        assertEquals(Grade.EXTREME, TrailMetrics.mountainBikeGrade(4, 5_000.0))
        assertEquals(Grade.HARD, TrailMetrics.mountainBikeGrade(3, 5_000.0))
        assertEquals(Grade.EASY, TrailMetrics.mountainBikeGrade(0, 5_000.0))
        assertEquals(Grade.EXTREME, TrailMetrics.offroadGrade("grade5"))
        assertEquals(Grade.EASY, TrailMetrics.offroadGrade(null))
        assertEquals(Grade.HARD, TrailMetrics.cyclingGrade(80_000.0, null))
        assertEquals(Grade.MODERATE, TrailMetrics.cyclingGrade(10_000.0, 500.0))
    }

    @Test
    fun durationAddsTimeForClimbing() {
        val flat = TrailMetrics.durationMinutes(TrailMetrics.Kind.HIKING, 8_000.0, 0.0)
        val hilly = TrailMetrics.durationMinutes(TrailMetrics.Kind.HIKING, 8_000.0, 500.0)
        assertEquals(120, flat)
        assertEquals(180, hilly)
        assertEquals(flat, TrailMetrics.durationMinutes(TrailMetrics.Kind.HIKING, 8_000.0, null))
        assertTrue(TrailMetrics.durationMinutes(TrailMetrics.Kind.CYCLING, 8_000.0, 0.0) < flat)
    }

    @Test
    fun elevationSamplingSpreadsPointsByDistanceAndKeepsTheEnds() {
        val a = LatLon(32.0, 35.0)
        val path = (0..1000).map { Geo.destination(a, 90.0, it * 10.0) }
        val idx = ElevationProfile.sampleIndices(path, 50)
        assertTrue(idx.size in 49..52)
        assertEquals(0, idx.first())
        assertEquals(1000, idx.last())
        assertEquals(idx.sorted(), idx)
        assertEquals((0..9).toList(), ElevationProfile.sampleIndices(path.take(10), 50))
    }

    @Test
    fun climbFromDemSamplesIgnoresSmallNoise() {
        val flat = ElevationProfile.climb(listOf(100.0, 101.5, 99.0, 100.5, 100.0, 101.0))!!
        assertEquals(0.0, flat.gainM, 0.1)
        val hill = ElevationProfile.climb(listOf(100.0, 150.0, 200.0, 190.0, 260.0, 240.0))!!
        assertEquals(170.0, hill.gainM, 1.0)
        assertEquals(30.0, hill.lossM, 1.0)
        assertEquals(260.0, hill.maxM, 0.0)
        assertNotNull(ElevationProfile.climb(listOf(1.0, 2.0)))
        assertNull(ElevationProfile.climb(listOf(1.0)))
    }
}
