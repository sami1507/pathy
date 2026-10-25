package com.example.pathy.core

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.track.CalorieEstimator
import com.example.pathy.core.track.ElevationAccumulator
import com.example.pathy.core.track.FixDecision
import com.example.pathy.core.track.GpsFix
import com.example.pathy.core.track.RecorderState
import com.example.pathy.core.track.RejectReason
import com.example.pathy.core.track.SportType
import com.example.pathy.core.track.TrackFilter
import com.example.pathy.core.track.TrackRecorderCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TrackTest {

    private val origin = LatLon(32.0, 35.0)

    private fun fix(p: LatLon, t: Long, acc: Float = 5f, alt: Double? = null) =
        GpsFix(p.lat, p.lon, alt, acc, null, t)

    // ---- TrackFilter ------------------------------------------------------------------------

    @Test
    fun poorAccuracyIsRejected() {
        val f = TrackFilter(SportType.HIKING)
        assertEquals(FixDecision.Reject(RejectReason.LOW_ACCURACY), f.evaluate(null, fix(origin, 0, acc = 80f)))
        assertEquals(FixDecision.Reject(RejectReason.LOW_ACCURACY), f.evaluate(null, GpsFix(32.0, 35.0, null, null, null, 0)))
    }

    @Test
    fun aGpsJumpIsRejectedButBriskWalkingIsNot() {
        val f = TrackFilter(SportType.HIKING)
        val first = fix(origin, 0)
        // 500 m in 5 s is 100 m/s: impossible on foot.
        val jump = fix(Geo.destination(origin, 90.0, 500.0), 5_000)
        assertEquals(FixDecision.Reject(RejectReason.GPS_JUMP), f.evaluate(first, jump))
        // 10 m in 5 s is 2 m/s: a normal pace.
        val step = fix(Geo.destination(origin, 90.0, 10.0), 5_000)
        assertTrue(f.evaluate(first, step) is FixDecision.Accept)
    }

    @Test
    fun wanderWhileStandingStillIsNotMovement() {
        val f = TrackFilter(SportType.HIKING)
        val first = fix(origin, 0)
        val wander = fix(Geo.destination(origin, 45.0, 2.0), 5_000)
        assertEquals(FixDecision.Stationary, f.evaluate(first, wander))
    }

    @Test
    fun outOfOrderFixesAreRejected() {
        val f = TrackFilter(SportType.HIKING)
        val first = fix(origin, 10_000)
        assertEquals(FixDecision.Reject(RejectReason.OUT_OF_ORDER), f.evaluate(first, fix(origin, 9_000)))
    }

    // ---- ElevationAccumulator ----------------------------------------------------------------

    @Test
    fun noiseOnAFlatWalkAddsNoClimb() {
        val rnd = Random(7)
        val acc = ElevationAccumulator()
        // 600 readings of a flat path at 100 m with +-2.5 m of receiver noise.
        repeat(600) { acc.add(100.0 + (rnd.nextDouble() - 0.5) * 5.0) }
        // The old "sum every positive difference" method would report well over 500 m here.
        assertTrue("gain was ${acc.gainM}", acc.gainM < 10.0)
        assertTrue("loss was ${acc.lossM}", acc.lossM < 10.0)
    }

    @Test
    fun aRealClimbIsMeasuredThroughNoise() {
        val rnd = Random(11)
        val acc = ElevationAccumulator()
        for (i in 0..400) acc.add(100.0 + i * 0.25 + (rnd.nextDouble() - 0.5) * 4.0) // 100 m up
        assertEquals(100.0, acc.gainM, 12.0)
        assertTrue(acc.lossM < 12.0)
    }

    @Test
    fun upThenDownCountsBothDirections() {
        val acc = ElevationAccumulator()
        for (i in 0..100) acc.add(200.0 + i)       // +100
        for (i in 0..100) acc.add(300.0 - i * 1.0)  // -100
        assertEquals(100.0, acc.gainM, 8.0)
        assertEquals(100.0, acc.lossM, 8.0)
        assertEquals(300.0, acc.maxM!!, 4.0)
    }

    @Test
    fun aSingleSpikeIsSmoothedAway() {
        val acc = ElevationAccumulator()
        repeat(10) { acc.add(100.0) }
        acc.add(180.0)   // one bad reading
        repeat(10) { acc.add(100.0) }
        assertTrue("gain was ${acc.gainM}", acc.gainM < 5.0)
    }

    // ---- TrackRecorderCore -------------------------------------------------------------------

    /** Walks east at [mps] for [seconds], one fix every [every] seconds. */
    private fun walk(core: TrackRecorderCore, from: LatLon, startMs: Long, seconds: Int, mps: Double, every: Int = 5, alt: (Int) -> Double? = { null }): Pair<LatLon, Long> {
        var pos = from
        var t = startMs
        var s = 0
        while (s < seconds) {
            s += every
            t += every * 1000L
            pos = Geo.destination(pos, 90.0, mps * every)
            core.onFix(fix(pos, t, alt = alt(s)))
        }
        return pos to t
    }

    @Test
    fun distanceOfASteadyWalkIsAccurate() {
        val core = TrackRecorderCore(SportType.HIKING)
        core.start(0)
        core.onFix(fix(origin, 0))
        walk(core, origin, 0, 715, 1.4) // about 1 km
        val stats = core.stats(715_000)
        assertEquals(1_001.0, stats.distanceM, 25.0)
        assertEquals(5.04, stats.averageSpeedKmh, 0.4)
        assertTrue(stats.paceMinPerKm in 10.0..14.0)
        assertTrue(stats.calories > 40)
    }

    @Test
    fun standingStillPausesAutomaticallyAndMovingResumes() {
        val core = TrackRecorderCore(SportType.HIKING, autoPauseAfterMs = 20_000)
        core.start(0)
        core.onFix(fix(origin, 0))
        // 60 s of GPS wander in place.
        for (i in 1..12) core.onFix(fix(Geo.destination(origin, (i * 40.0), 1.5), i * 5_000L))
        assertEquals(RecorderState.AUTO_PAUSED, core.state)
        val frozen = core.stats(60_000).distanceM
        // Walk off again.
        walk(core, origin, 60_000, 60, 1.4)
        assertEquals(RecorderState.RECORDING, core.state)
        assertTrue(core.stats(120_000).distanceM > frozen + 60.0)
    }

    @Test
    fun theTimeSpentPausedIsNotCountedAndTheGapIsNotMeasured() {
        val core = TrackRecorderCore(SportType.HIKING)
        core.start(0)
        core.onFix(fix(origin, 0))
        val (pos, t) = walk(core, origin, 0, 100, 1.4)
        core.pause(t)
        assertNull(core.onFix(fix(Geo.destination(pos, 90.0, 3.0), t + 1_000)))
        // Pause for 10 minutes, then carry on from a place 2 km away (user drove).
        val resumeAt = t + 600_000
        core.resume(resumeAt)
        val far = Geo.destination(pos, 90.0, 2_000.0)
        core.onFix(fix(far, resumeAt + 5_000))
        walk(core, far, resumeAt + 5_000, 100, 1.4)
        val stats = core.stats(resumeAt + 105_000)
        // About 200 m walked in total, not 2 km.
        assertEquals(280.0, stats.distanceM, 40.0)
        // 100 s + 105 s of walking time, not 10 minutes more.
        assertTrue("elapsed ${stats.elapsedMs}", stats.elapsedMs < 230_000)
    }

    @Test
    fun climbingIsMeasuredFromAltitude() {
        val core = TrackRecorderCore(SportType.HIKING)
        core.start(0)
        core.onFix(fix(origin, 0, alt = 100.0))
        walk(core, origin, 0, 1_400, 1.4, alt = { s -> 100.0 + s * 0.1 }) // +140 m
        val stats = core.stats(1_400_000)
        assertEquals(140.0, stats.elevationGainM, 20.0)
        assertTrue(stats.elevationLossM < 10.0)
        assertNotNull(stats.maxElevationM)
    }

    @Test
    fun replayingStoredPointsRebuildsTheSameNumbers() {
        val live = TrackRecorderCore(SportType.HIKING)
        live.start(0)
        live.onFix(fix(origin, 0, alt = 50.0))
        walk(live, origin, 0, 600, 1.4, alt = { s -> 50.0 + s * 0.05 })
        val liveStats = live.stats(600_000)

        val restored = TrackRecorderCore(SportType.HIKING)
        restored.restoreClock(0, 0, RecorderState.RECORDING)
        live.points.forEach { restored.replay(it) }
        val restoredStats = restored.stats(600_000)

        assertEquals(liveStats.distanceM, restoredStats.distanceM, 0.5)
        assertEquals(liveStats.elevationGainM, restoredStats.elevationGainM, 1.0)
        assertEquals(live.points.size, restored.points.size)
    }

    @Test
    fun badFixesDoNotEnterTheTrack() {
        val core = TrackRecorderCore(SportType.HIKING)
        core.start(0)
        assertNotNull(core.onFix(fix(origin, 0)))
        assertNull(core.onFix(fix(Geo.destination(origin, 0.0, 800.0), 5_000)))          // jump
        assertNull(core.onFix(fix(Geo.destination(origin, 90.0, 8.0), 10_000, acc = 90f))) // poor accuracy
        assertEquals(1, core.points.size)
        assertEquals(0.0, core.stats(10_000).distanceM, 0.001)
    }

    @Test
    fun caloriesScaleWithTimeWeightAndClimb() {
        val hour = 3_600_000L
        val flat = CalorieEstimator.estimate(SportType.HIKING, 70.0, hour, 4.0, 0.0)
        val steep = CalorieEstimator.estimate(SportType.HIKING, 70.0, hour, 3.0, 600.0)
        val heavy = CalorieEstimator.estimate(SportType.HIKING, 90.0, hour, 4.0, 0.0)
        assertEquals(420, flat)
        assertTrue(steep > flat)
        assertTrue(heavy > flat)
        assertEquals(0, CalorieEstimator.estimate(SportType.HIKING, 70.0, 0, 0.0, 0.0))
    }
}
