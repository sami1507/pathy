package com.example.pathy.core

import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.osm.OsmKind
import com.example.pathy.core.osm.OverpassQueries
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The public map servers answer in about three seconds when a filter is an exact match and give up after
 * thirty when a filter on the road type is a regular expression (measured around Wadi Qelt, 3.6 km). A search
 * that times out leaves the map with no trails, so these filters must stay exact.
 */
class OverpassQuerySpeedTest {

    private val everything = OverpassQueries.nearby(LatLon(31.833, 35.4165), 3000, OsmKind.values().toSet())

    @Test
    fun theRoadTypeIsNeverMatchedWithARegularExpression() {
        assertFalse(everything, Regex("""\["highway"~""").containsMatchIn(everything))
    }

    @Test
    fun theTrackGradeIsNeverMatchedWithARegularExpression() {
        assertFalse(everything, Regex("""\["tracktype"~""").containsMatchIn(everything))
    }

    @Test
    fun signedPathsAndFootwaysAreStillAskedFor() {
        assertTrue(everything.contains("""way["highway"="path"]["sac_scale"]"""))
        assertTrue(everything.contains("""way["highway"="footway"]["sac_scale"]"""))
    }

    @Test
    fun mountainBikeAndRoughTrackSearchesKeepEveryRoadTypeTheyHadBefore() {
        listOf("path", "track", "cycleway").forEach { type ->
            assertTrue(type, everything.contains("""way["highway"="$type"]["mtb:scale"]"""))
        }
        assertTrue(everything.contains("""["tracktype"="grade4"]"""))
        assertTrue(everything.contains("""["tracktype"="grade5"]"""))
    }
}
