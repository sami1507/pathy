package com.example.pathy.core

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.nav.NavPhase
import com.example.pathy.core.nav.RouteNavigator
import com.example.pathy.core.nav.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNavigatorTest {

    private val start = LatLon(32.0, 35.0)

    /** A route made of straight legs: each pair is (compass heading, length in metres), vertices every 20 m. */
    private fun route(vararg legs: Pair<Double, Double>): List<LatLon> {
        val pts = arrayListOf(start)
        for ((heading, length) in legs) {
            var left = length
            while (left > 0) {
                val step = minOf(20.0, left)
                pts += Geo.destination(pts.last(), heading, step)
                left -= step
            }
        }
        return pts
    }

    private fun first(route: List<LatLon>) = RouteNavigator(route).maneuvers.first()

    // ---- the turn direction must come from the route, never from the compass ----------------

    @Test
    fun eastThenNorthIsALeftTurn() = assertEquals(Turn.LEFT, first(route(90.0 to 300.0, 0.0 to 300.0)).turn)

    @Test
    fun eastThenSouthIsARightTurn() = assertEquals(Turn.RIGHT, first(route(90.0 to 300.0, 180.0 to 300.0)).turn)

    @Test
    fun westThenNorthIsARightTurn() = assertEquals(Turn.RIGHT, first(route(270.0 to 300.0, 0.0 to 300.0)).turn)

    @Test
    fun northThenWestIsALeftTurn() = assertEquals(Turn.LEFT, first(route(0.0 to 300.0, 270.0 to 300.0)).turn)

    @Test
    fun theOldBugCase() {
        // The old code said "turn right" whenever the next point lay east of the walker. Walking
        // east and then turning north must be LEFT even though the corner is east of the walker.
        val nav = RouteNavigator(route(90.0 to 300.0, 0.0 to 300.0))
        val walker = Geo.destination(start, 90.0, 100.0)
        val state = nav.update(walker, 5f)
        assertEquals(NavPhase.ON_ROUTE, state.phase)
        assertEquals(Turn.LEFT, state.nextManeuver!!.turn)
        assertEquals(200.0, state.distanceToManeuverM!!, 8.0)
    }

    @Test
    fun turnSharpnessIsClassified() {
        assertEquals(Turn.SLIGHT_RIGHT, RouteNavigator.classify(40.0))
        assertEquals(Turn.RIGHT, RouteNavigator.classify(90.0))
        assertEquals(Turn.SHARP_LEFT, RouteNavigator.classify(-140.0))
        assertEquals(Turn.U_TURN, RouteNavigator.classify(175.0))
        assertEquals(Turn.SLIGHT_LEFT, RouteNavigator.classify(-45.0))
    }

    @Test
    fun aStraightRouteHasNoTurns() {
        assertTrue(RouteNavigator(route(90.0 to 600.0)).maneuvers.isEmpty())
    }

    @Test
    fun smallGpsWiggleIsNotATurn() {
        // A line that zig-zags by 3 m: below the simplification tolerance.
        val pts = arrayListOf(start)
        for (i in 1..40) {
            val ahead = Geo.destination(pts.last(), 90.0, 15.0)
            pts += Geo.destination(ahead, if (i % 2 == 0) 0.0 else 180.0, 3.0)
        }
        assertTrue(RouteNavigator(pts).maneuvers.isEmpty())
    }

    @Test
    fun twoTurnsCloseTogetherAreMerged() {
        val r = route(90.0 to 200.0, 0.0 to 15.0, 90.0 to 200.0)
        // A 15 m jog: left then right within 25 m is one junction, not two announcements.
        assertTrue(RouteNavigator(r).maneuvers.size <= 1)
    }

    // ---- progress, starting and arriving ----------------------------------------------------

    @Test
    fun navigationStartsAnywhereAlongTheRouteNotJustAtTheFirstPoint() {
        val nav = RouteNavigator(route(90.0 to 1_000.0))
        val state = nav.update(Geo.destination(Geo.destination(start, 90.0, 400.0), 0.0, 12.0), 8f)
        assertEquals(NavPhase.ON_ROUTE, state.phase)
        assertEquals(400.0, state.alongM, 6.0)
        assertEquals(600.0, state.remainingM, 6.0)
    }

    @Test
    fun farFromTheRouteTheWalkerIsToldToHeadForIt() {
        val nav = RouteNavigator(route(90.0 to 1_000.0))
        val here = Geo.destination(start, 0.0, 300.0)           // 300 m north of the start
        val state = nav.update(here, 8f)
        assertEquals(NavPhase.APPROACHING, state.phase)
        // The route lies due south of here.
        assertEquals(180.0, state.bearingToRouteDeg!!, 3.0)
    }

    @Test
    fun poorGpsAccuracyAtTheStartStillJoinsTheRoute() {
        // The old code needed to be within 5 m of the first point. 20 m off with poor GPS must work.
        val nav = RouteNavigator(route(90.0 to 1_000.0))
        val state = nav.update(Geo.destination(start, 180.0, 20.0), 25f)
        assertEquals(NavPhase.ON_ROUTE, state.phase)
    }

    @Test
    fun progressFollowsTheWalkerAndArrivalIsDetected() {
        val nav = RouteNavigator(route(90.0 to 500.0, 0.0 to 500.0))
        var last = 0f
        for (m in 0..1000 step 10) {
            val leg = if (m <= 500) Geo.destination(start, 90.0, m.toDouble())
            else Geo.destination(Geo.destination(start, 90.0, 500.0), 0.0, (m - 500).toDouble())
            val s = nav.update(leg, 6f)
            assertTrue(s.progress >= last - 0.01f)
            last = s.progress
            if (m < 980) assertTrue("phase at $m was ${s.phase}", s.phase == NavPhase.ON_ROUTE)
        }
        val end = nav.update(Geo.destination(Geo.destination(start, 90.0, 500.0), 0.0, 495.0), 6f)
        assertEquals(NavPhase.ARRIVED, end.phase)
        assertNull(end.nextManeuver)
    }

    @Test
    fun aLoopDoesNotFinishTheMomentItStarts() {
        // Out 500 m east, back west to the start: start and end are the same place.
        val nav = RouteNavigator(route(90.0 to 500.0, 270.0 to 500.0))
        val atStart = nav.update(start, 5f)
        assertEquals(NavPhase.ON_ROUTE, atStart.phase)
    }

    // ---- off route -------------------------------------------------------------------------

    @Test
    fun offRouteNeedsSeveralFixesAndClearsOnReturn() {
        val nav = RouteNavigator(route(90.0 to 1_000.0))
        val onRoute = Geo.destination(start, 90.0, 300.0)
        assertEquals(NavPhase.ON_ROUTE, nav.update(onRoute, 5f).phase)

        val lost = Geo.destination(onRoute, 0.0, 80.0)
        // One or two stray fixes are not enough...
        assertEquals(NavPhase.ON_ROUTE, nav.update(lost, 5f).phase)
        assertEquals(NavPhase.ON_ROUTE, nav.update(lost, 5f).phase)
        // ...the third in a row is.
        val off = nav.update(lost, 5f)
        assertEquals(NavPhase.OFF_ROUTE, off.phase)
        assertEquals(80.0, off.offsetM, 3.0)
        assertEquals(180.0, off.bearingToRouteDeg!!, 3.0)

        // Walking back toward the line: still off until clearly back.
        assertEquals(NavPhase.OFF_ROUTE, nav.update(Geo.destination(onRoute, 0.0, 30.0), 5f).phase)
        assertEquals(NavPhase.ON_ROUTE, nav.update(Geo.destination(onRoute, 0.0, 10.0), 5f).phase)
    }

    @Test
    fun poorAccuracyWidensTheOffRouteLimit() {
        val nav = RouteNavigator(route(90.0 to 1_000.0))
        nav.update(Geo.destination(start, 90.0, 300.0), 5f)
        // 50 m away is off route with good GPS, but with a 60 m accuracy radius it is just noise.
        val noisy = Geo.destination(Geo.destination(start, 90.0, 300.0), 0.0, 50.0)
        repeat(5) { assertEquals(NavPhase.ON_ROUTE, nav.update(noisy, 60f).phase) }
    }

    @Test
    fun theNextManeuverAdvancesAsTheWalkerPassesTurns() {
        val nav = RouteNavigator(route(90.0 to 300.0, 0.0 to 300.0, 90.0 to 300.0))
        val corner1 = Geo.destination(start, 90.0, 300.0)
        val before = nav.update(Geo.destination(start, 90.0, 250.0), 5f)
        assertNotNull(before.nextManeuver)
        assertEquals(Turn.LEFT, before.nextManeuver!!.turn)
        val after = nav.update(Geo.destination(corner1, 0.0, 100.0), 5f)
        assertEquals(Turn.RIGHT, after.nextManeuver!!.turn)
    }
}
