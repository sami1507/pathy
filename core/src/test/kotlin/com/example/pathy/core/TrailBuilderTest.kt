package com.example.pathy.core

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.osm.OsmClassifier
import com.example.pathy.core.osm.OsmKind
import com.example.pathy.core.osm.OsmTrail
import com.example.pathy.core.osm.OverpassQueries
import com.example.pathy.core.osm.TrailBuilder
import com.example.pathy.core.trail.Grade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailBuilderTest {

    private val start = LatLon(32.0, 35.0)

    private fun line(lengthM: Double, step: Double = 20.0, heading: Double = 45.0): List<LatLon> {
        val pts = arrayListOf(start)
        var left = lengthM
        while (left > 0) {
            val s = minOf(step, left)
            pts += Geo.destination(pts.last(), heading, s)
            left -= s
        }
        return pts
    }

    private fun osm(id: String, tags: Map<String, String>, path: List<LatLon>, relation: Boolean = false) =
        OsmTrail(id, tags, path, emptyList(), relation)

    // ---- classification -------------------------------------------------------------------

    @Test
    fun routeRelationsAreClassifiedByTheirRouteType() {
        assertEquals(OsmKind.HIKING, OsmClassifier.classify(mapOf("route" to "hiking"), true))
        assertEquals(OsmKind.MTB, OsmClassifier.classify(mapOf("route" to "mtb"), true))
        assertEquals(OsmKind.CYCLING, OsmClassifier.classify(mapOf("route" to "bicycle"), true))
        assertNull("a bus route is not a trail", OsmClassifier.classify(mapOf("route" to "bus"), true))
    }

    @Test
    fun pavementsAndUnnamedFieldPathsAreNotTrails() {
        assertNull(OsmClassifier.classify(mapOf("highway" to "footway"), false))
        assertNull(OsmClassifier.classify(mapOf("highway" to "footway", "footway" to "sidewalk"), false))
        assertEquals(OsmKind.HIKING, OsmClassifier.classify(mapOf("highway" to "path", "name" to "Nahal Amud"), false))
        assertEquals(OsmKind.HIKING, OsmClassifier.classify(mapOf("highway" to "path", "sac_scale" to "mountain_hiking"), false))
    }

    @Test
    fun offroadMtbAndCyclingWays() {
        assertEquals(OsmKind.OFFROAD, OsmClassifier.classify(mapOf("highway" to "track", "tracktype" to "grade5"), false))
        assertEquals(OsmKind.MTB, OsmClassifier.classify(mapOf("highway" to "path", "mtb:scale" to "2"), false))
        assertEquals(OsmKind.CYCLING, OsmClassifier.classify(mapOf("highway" to "cycleway", "name" to "Coastal"), false))
    }

    // ---- queries ----------------------------------------------------------------------------

    @Test
    fun theQueryAsksForGeometryClippedToTheSearchSquareAndOnlyForWantedKinds() {
        val q = OverpassQueries.nearby(start, 3000, setOf(OsmKind.HIKING))
        assertTrue(q.startsWith("[out:json]"))
        assertTrue(q.contains("relation[\"route\"~\"^(hiking|foot|walking)$\"](around:3000,32.000000,35.000000)"))
        assertTrue(q.contains("out meta geom("))
        assertFalse("no mtb part when only hiking was asked for", q.contains("mtb"))
        assertEquals("", OverpassQueries.nearby(start, 3000, emptySet()))
    }

    @Test
    fun coordinatesAreWrittenWithADotInAnyLanguage() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
            val q = OverpassQueries.nearby(LatLon(32.5, 35.25), 1000, setOf(OsmKind.HIKING))
            assertTrue(q.contains("32.500000,35.250000"))
            assertFalse(q.contains("٣"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    // ---- building trails --------------------------------------------------------------------

    @Test
    fun shortFragmentsAreDropped() {
        assertNull(TrailBuilder.from(osm("w1", mapOf("highway" to "path", "name" to "x"), line(300.0))))
    }

    @Test
    fun anUnknownClimbStaysUnknownAndIsNotInventedFromTheNumberOfPoints() {
        val t = TrailBuilder.from(osm("w1", mapOf("highway" to "path", "name" to "Long Path"), line(5_000.0, step = 5.0)))!!
        assertNull("1000 points must not become '2000 m of climb'", t.gainM)
        assertEquals(Grade.EASY, t.grade)
        assertTrue("a 5 km walk takes about an hour and a quarter, not ${t.durationMin} min", t.durationMin in 73..77)
    }

    @Test
    fun aClimbStatedInOsmIsUsedForGradeAndTime() {
        val t = TrailBuilder.from(
            osm("r1", mapOf("route" to "hiking", "name" to "Peak", "ascent" to "900 m"), line(8_000.0), relation = true)
        )!!
        assertEquals(900.0, t.gainM!!, 0.01)
        assertTrue("steep and long is at least hard", t.grade >= Grade.HARD)
        assertTrue("climb adds time", t.durationMin > 8_000 / 4000 * 60)
    }

    @Test
    fun theSacScaleSetsAMinimumGrade() {
        val t = TrailBuilder.from(osm("w1", mapOf("highway" to "path", "sac_scale" to "alpine_hiking"), line(1_000.0)))!!
        assertEquals(Grade.EXTREME, t.grade)
    }

    @Test
    fun aLoopIsRecognised() {
        val out = line(500.0)
        val loop = out + out.reversed().drop(1) // out and back to the same point
        assertTrue(TrailBuilder.from(osm("w1", mapOf("highway" to "path", "name" to "Loop"), loop))!!.isLoop)
        assertFalse(TrailBuilder.from(osm("w2", mapOf("highway" to "path", "name" to "Line"), out))!!.isLoop)
    }

    @Test
    fun theNamePrefersTheLanguageOfTheApp() {
        val tags = mapOf("highway" to "path", "name" to "Nahal Amud", "name:ar" to "وادي عمود", "name:he" to "נחל עמוד")
        assertEquals("وادي عمود", TrailBuilder.from(osm("w1", tags, line(900.0)), "ar")!!.name)
        assertEquals("Nahal Amud", TrailBuilder.from(osm("w1", tags, line(900.0)), "fr")!!.name)
        assertNull("no name at all stays null", TrailBuilder.from(osm("w2", mapOf("highway" to "path", "sac_scale" to "hiking"), line(900.0)))!!.name)
    }

    @Test
    fun aVeryLongPathIsThinnedButKeepsItsEndsAndShape() {
        // A 40 km wavy line with a point every metre.
        val pts = (0..40_000).map { i ->
            Geo.destination(Geo.destination(start, 90.0, i.toDouble()), 0.0, Math.sin(i / 800.0) * 300.0)
        }
        val thin = TrailBuilder.thin(pts)
        assertTrue(thin.size <= TrailBuilder.MAX_POINTS)
        assertEquals(pts.first(), thin.first())
        assertEquals(pts.last(), thin.last())
        assertEquals("length is preserved within 1%", Geo.length(pts), Geo.length(thin), Geo.length(pts) * 0.01)
    }

    @Test
    fun duplicatesAreMergedAndTheNamedOneWins() {
        val path = line(1_200.0)
        val unnamed = TrailBuilder.from(osm("w9", mapOf("highway" to "path", "sac_scale" to "hiking"), path))!!
        val named = TrailBuilder.from(osm("w1", mapOf("highway" to "path", "name" to "Same"), path))!!
        val again = TrailBuilder.from(osm("w1", mapOf("highway" to "path", "name" to "Same"), path))!!
        val other = TrailBuilder.from(osm("w2", mapOf("highway" to "path", "name" to "Same"), line(1_200.0, heading = 200.0).map { LatLon(it.lat + 0.1, it.lon) }))!!

        val result = TrailBuilder.dedupe(listOf(unnamed, named, again, other))
        assertEquals(3, result.size)
        assertEquals(1, result.count { it.id == "w1" })
        assertNotNull(result.find { it.id == "w2" })
    }

    @Test
    fun leadingNumbersAreReadFromFreeText() {
        assertEquals(250.0, TrailBuilder.leadingNumber("250 m")!!, 0.0)
        assertEquals(250.5, TrailBuilder.leadingNumber("250,5")!!, 0.0)
        assertNull(TrailBuilder.leadingNumber("steep"))
    }
}

class TrailTaggingTest {
    private fun flags(kind: OsmKind = OsmKind.HIKING, vararg tags: Pair<String, String>) =
        com.example.pathy.core.osm.TrailTagging.flags(mapOf(*tags), kind)

    @Test
    fun namesInThreeLanguagesAreRecognised() {
        assertTrue(flags(tags = arrayOf("name" to "Nahal Amud stream trail")).water)
        assertTrue(flags(tags = arrayOf("name" to "נחל עמוד")).water)
        assertTrue(flags(tags = arrayOf("name" to "وادي القلط")).water)
        assertTrue(flags(tags = arrayOf("name" to "שמורת הר מירון")).forest)
        assertTrue(flags(tags = arrayOf("name" to "مطل الكرمل")).viewpoint)
        assertFalse(flags(tags = arrayOf("name" to "Ordinary Road")).water)
    }

    @Test
    fun difficultyAndAccessibilityComeFromTags() {
        assertTrue(flags(tags = arrayOf("sac_scale" to "demanding_mountain_hiking")).extremeSection)
        assertTrue(flags(kind = OsmKind.MTB, tags = arrayOf("mtb:scale" to "3")).extremeSection)
        assertFalse(flags(kind = OsmKind.MTB, tags = arrayOf("mtb:scale" to "1")).extremeSection)
        assertTrue(flags(tags = arrayOf("wheelchair" to "yes")).accessible)
    }

    @Test
    fun whoTheTrailSuitsFollowsItsKind() {
        val hike = flags(OsmKind.HIKING)
        assertTrue(hike.walking); assertFalse(hike.bicycle); assertFalse(hike.vehicle)
        val mtb = flags(OsmKind.MTB)
        assertTrue(mtb.bicycle); assertFalse(mtb.walking)
        val offroad = flags(OsmKind.OFFROAD, "highway" to "track")
        assertTrue(offroad.vehicle); assertFalse(offroad.walking)
        assertTrue("a signed footpath is also fine to walk", flags(OsmKind.OFFROAD, "foot" to "yes").walking)
    }
}
