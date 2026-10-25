package com.example.pathy.core

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.nav.Cue
import com.example.pathy.core.nav.RouteNavigator
import com.example.pathy.core.nav.Turn
import com.example.pathy.core.nav.VoiceCueScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCuesTest {

    private val start = LatLon(32.0, 35.0)

    /** East 400 m, then north 400 m: one left turn at 400 m. */
    private val route: List<LatLon> = buildList {
        add(start)
        for ((heading, length) in listOf(90.0 to 400.0, 0.0 to 400.0)) {
            var left = length
            while (left > 0) {
                val step = minOf(20.0, left)
                add(Geo.destination(last(), heading, step))
                left -= step
            }
        }
    }

    private fun walk(nav: RouteNavigator, scheduler: VoiceCueScheduler, alongM: Double): Cue? =
        scheduler.next(nav.update(Geo.destination(start, 90.0, alongM), 5f))

    @Test
    fun aTurnIsAnnouncedFarThenNearThenNowAndEachOnlyOnce() {
        val nav = RouteNavigator(route)
        val scheduler = VoiceCueScheduler()
        assertTrue(walk(nav, scheduler, 5.0) is Cue.Joined)

        val spoken = ArrayList<Cue>()
        var along = 10.0
        while (along < 395.0) {
            walk(nav, scheduler, along)?.let { spoken += it }
            along += 3.0
        }

        val ahead = spoken.filterIsInstance<Cue.Ahead>()
        assertEquals("far and near", 2, ahead.size)
        assertEquals(Turn.LEFT, ahead.first().maneuver.turn)
        assertTrue("far cue is spoken from about 150 m", ahead.first().distanceM in 100..150)
        assertTrue("near cue is spoken from about 40 m", ahead.last().distanceM in 30..40)
        assertEquals(1, spoken.count { it is Cue.TurnNow })
    }

    @Test
    fun noRepeatWhileStandingStill() {
        val nav = RouteNavigator(route)
        val scheduler = VoiceCueScheduler()
        walk(nav, scheduler, 5.0)
        assertTrue(walk(nav, scheduler, 280.0) is Cue.Ahead)
        repeat(20) { assertNull(walk(nav, scheduler, 280.0)) }
    }

    @Test
    fun joiningCloseToATurnDoesNotReplayTheFarWarning() {
        val nav = RouteNavigator(route)
        val scheduler = VoiceCueScheduler()
        assertTrue(walk(nav, scheduler, 350.0) is Cue.Joined)
        val cue = walk(nav, scheduler, 365.0) as Cue.Ahead
        assertTrue("only the near warning, not a 'far' one as well", cue.distanceM <= 40)
        assertNull(walk(nav, scheduler, 366.0))
    }

    @Test
    fun straightingOffTheRouteIsSaidOnceAndComingBackIsSaidOnce() {
        val nav = RouteNavigator(route)
        val scheduler = VoiceCueScheduler()
        walk(nav, scheduler, 5.0)
        walk(nav, scheduler, 100.0)

        val away = Geo.destination(Geo.destination(start, 90.0, 120.0), 180.0, 90.0)
        val cues = (1..8).mapNotNull { scheduler.next(nav.update(away, 5f)) }
        assertEquals(listOf<Cue>(Cue.OffRoute), cues)

        val back = Geo.destination(start, 90.0, 130.0)
        val returned = (1..3).mapNotNull { scheduler.next(nav.update(back, 5f)) }
        assertEquals(listOf<Cue>(Cue.BackOnRoute), returned)
    }

    @Test
    fun arrivalIsAnnouncedOnce() {
        val nav = RouteNavigator(route)
        val scheduler = VoiceCueScheduler()
        val end = route.last()
        val cues = ArrayList<Cue>()
        // Walk the whole route.
        for (p in route) scheduler.next(nav.update(p, 5f))?.let { cues += it }
        repeat(5) { scheduler.next(nav.update(end, 5f))?.let { cues += it } }
        assertEquals(1, cues.count { it is Cue.Arrived })
    }
}
