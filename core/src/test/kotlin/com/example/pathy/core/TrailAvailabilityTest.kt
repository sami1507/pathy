package com.example.pathy.core

import com.example.pathy.core.osm.Availability
import com.example.pathy.core.osm.OsmKind
import com.example.pathy.core.osm.OverpassParser
import com.example.pathy.core.osm.TrailAvailability
import com.example.pathy.core.osm.TrailBuilder
import com.example.pathy.core.osm.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrailAvailabilityTest {

    private val now = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli()
    private val recent = Instant.parse("2026-05-01T00:00:00Z").toEpochMilli()
    private val old = Instant.parse("2017-01-01T00:00:00Z").toEpochMilli()

    private fun assess(vararg tags: Pair<String, String>, kind: OsmKind = OsmKind.HIKING, relation: Boolean = false, edit: Long? = recent) =
        TrailAvailability.assess(kind, mapOf(*tags), relation, edit, now)

    // ---- what must never be sent to a hiker ---------------------------------------------------

    @Test
    fun closedAbandonedAndPlannedTrailsAreNotListed() {
        assertEquals(Availability.CLOSED, assess("highway" to "construction").availability)
        assertEquals(Availability.CLOSED, assess("highway" to "path", "construction" to "path").availability)
        assertEquals(Availability.CLOSED, assess("highway" to "path", "abandoned" to "yes").availability)
        assertEquals(Availability.CLOSED, assess("route" to "hiking", "state" to "proposed", relation = true).availability)
        assertEquals(Availability.CLOSED, assess("route" to "hiking", "operational_status" to "closed", relation = true).availability)
        assertEquals(Availability.CLOSED, assess("highway" to "path", "access" to "no").availability)
        assertFalse(assess("highway" to "path", "access" to "no").isListable)
    }

    @Test
    fun aPathThatOnlyExistsAsAMemoryInTheDataIsClosed() {
        assertEquals(Availability.CLOSED, assess("disused:highway" to "path", "name" to "Old track").availability)
        assertEquals(Availability.CLOSED, assess("abandoned:route" to "hiking").availability)
    }

    @Test
    fun privateLandAndMilitaryZonesAreRestricted() {
        assertEquals(Availability.RESTRICTED, assess("highway" to "path", "access" to "private").availability)
        assertEquals(Availability.RESTRICTED, assess("highway" to "path", "foot" to "no").availability)
        assertEquals(Availability.RESTRICTED, assess("highway" to "track", "military" to "yes", kind = OsmKind.OFFROAD).availability)
        assertFalse(assess("highway" to "path", "access" to "private").isListable)
    }

    @Test
    fun aSpecificPermissionBeatsAGeneralRestriction() {
        // Closed to cars, open to walkers: a hiking trail.
        assertEquals(Availability.OPEN, assess("highway" to "path", "access" to "no", "foot" to "yes").availability)
        // Private for everyone except cyclists: not a hiking trail, but fine for cycling.
        assertEquals(Availability.OPEN, assess("highway" to "track", "access" to "private", "bicycle" to "designated", kind = OsmKind.CYCLING).availability)
        assertEquals(Availability.RESTRICTED, assess("highway" to "track", "access" to "private", "bicycle" to "designated").availability)
    }

    // ---- what is shown with a warning --------------------------------------------------------

    @Test
    fun seasonalTrailsAreListedWithAWarning() {
        val a = assess("highway" to "path", "seasonal" to "winter")
        assertEquals(Availability.CONDITIONAL, a.availability)
        assertTrue(a.isListable)
        assertTrue(Warning.SEASONAL in a.warnings)
        assertEquals(Availability.OPEN, assess("highway" to "path", "seasonal" to "no").availability)
    }

    @Test
    fun poorVisibilityAndPendingFixesAreWarnings() {
        assertTrue(Warning.HARD_TO_FOLLOW in assess("highway" to "path", "trail_visibility" to "bad").warnings)
        assertTrue(Warning.NEEDS_CHECK in assess("highway" to "path", "fixme" to "check if path still exists").warnings)
    }

    // ---- how recent the information is ----------------------------------------------------------

    @Test
    fun aGroundCheckBeatsTheLastEditDate() {
        val a = assess("highway" to "path", "check_date" to "2026-03-15", edit = old)
        assertNotNull(a.verifiedAtMs)
        assertTrue("checked this year, so not outdated", Warning.OUTDATED !in a.warnings)
        assertEquals(a.verifiedAtMs, a.freshestMs)
    }

    @Test
    fun dataNobodyHasTouchedForYearsIsMarkedOutdated() {
        val stale = assess("highway" to "path", edit = old)
        assertTrue(Warning.OUTDATED in stale.warnings)
        assertTrue("still listed: old is not the same as closed", stale.isListable)
        assertTrue(Warning.OUTDATED !in assess("highway" to "path", edit = recent).warnings)
    }

    @Test
    fun checkDatesWrittenInDifferentWaysAreUnderstood() {
        assertNotNull(TrailAvailability.parseDate("2024-05-03"))
        assertNotNull(TrailAvailability.parseDate("2024-05"))
        assertNotNull(TrailAvailability.parseDate("2024"))
        assertNull(TrailAvailability.parseDate("yesterday"))
        assertNull(TrailAvailability.parseDate(""))
        assertNull("a year of 1066 is a typo", TrailAvailability.parseDate("1066-01-01"))
    }

    @Test
    fun aMarkedNamedRecentRouteOutranksABarePath() {
        val route = assess("route" to "hiking", "network" to "lwn", "name" to "Wadi", "osmc:symbol" to "red:white:red_stripe", relation = true).quality
        val bare = assess("highway" to "path", "sac_scale" to "hiking").quality
        assertTrue("route $route should beat path $bare", route > bare + 4)
    }

    // ---- against real OpenStreetMap data (Haifa, fetched from Overpass) ---------------------------

    @Test
    fun realHaifaDataGivesUsableMarkedTrailsAndNoPavements() {
        val json = javaClass.getResourceAsStream("/overpass_haifa_sample.json")!!.bufferedReader().readText()
        val osm = OverpassParser.parse(json)
        assertTrue("sample has elements", osm.size > 15)
        assertTrue("edit times are read from `out meta`", osm.count { it.lastEditMs != null } > osm.size / 2)

        val trails = osm.mapNotNull { TrailBuilder.from(it, "en", now) }
        assertTrue("several real trails survive", trails.size >= 8)
        assertTrue("every one is long enough to be worth a trip", trails.all { it.distanceM >= TrailBuilder.MIN_LENGTH_M })
        // The big regional route (the "Haifa wadis trail") is kept as one relation with its colour mark.
        val regional = trails.first { it.id == "osm_r3734116" }
        assertEquals("rwn", regional.network)
        assertEquals("green", regional.waymark)
        assertTrue(regional.assessment.quality >= 7)
        // Many marked routes have no name at all; they are still real trails and are told apart by colour.
        val unnamedMarked = trails.filter { it.name == null && it.waymark != null }
        assertTrue("unnamed marked routes exist in real data", unnamedMarked.isNotEmpty())
        // Urban footways (pavements named like streets) are not offered.
        assertTrue(trails.none { it.tags["highway"] == "footway" && !it.tags.containsKey("sac_scale") })
    }
}
