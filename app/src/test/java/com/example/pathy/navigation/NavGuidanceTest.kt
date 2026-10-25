package com.example.pathy.navigation

import android.content.Context
import android.content.res.Configuration
import com.example.pathy.R
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.nav.Cue
import com.example.pathy.core.nav.Maneuver
import com.example.pathy.core.nav.NavPhase
import com.example.pathy.core.nav.NavState
import com.example.pathy.core.nav.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Every sentence the navigator can say must be filled in correctly in all three languages: a wrong
 * placeholder in one translation crashes at the exact moment a walker needs the instruction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavGuidanceTest {

    private val here = LatLon(32.0, 35.0)

    private fun context(language: String): Context {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale(language)) }
        return base.createConfigurationContext(config)
    }

    private fun maneuver(turn: Turn) = Maneuver(turn, angleDeg = 90.0, alongM = 500.0, position = here)

    private fun state(phase: NavPhase, next: Maneuver? = null) = NavState(
        phase = phase, offsetM = 87.0, alongM = 400.0, remainingM = 2_300.0, progress = 0.4f,
        nextManeuver = next, distanceToManeuverM = next?.let { 120.0 },
        bearingToRouteDeg = 270.0, nearestRoutePoint = here
    )

    private val languages = listOf("en", "ar", "iw")

    @Test
    fun everyTurnHasWordsInEveryLanguage() {
        for (lang in languages) {
            val c = context(lang)
            val words = Turn.values().map { NavGuidance.turnWords(c, it) }
            assertTrue("$lang: every turn has text", words.all { it.isNotBlank() })
            assertEquals("$lang: each turn reads differently", Turn.values().size, words.toSet().size)
        }
    }

    @Test
    fun everyBannerIsCompleteInEveryLanguage() {
        for (lang in languages) {
            val c = context(lang)
            val banners = buildList {
                add(NavGuidance.of(c, null, null))
                for (phase in NavPhase.values()) {
                    add(NavGuidance.of(c, state(phase), headingDeg = 10f))
                    add(NavGuidance.of(c, state(phase), headingDeg = null))
                }
                for (turn in Turn.values()) add(NavGuidance.of(c, state(NavPhase.ON_ROUTE, maneuver(turn)), 0f))
            }
            for (b in banners) {
                assertTrue("$lang: text '${b.text}'", b.text.isNotBlank())
                assertFalse("$lang: unfilled placeholder in '${b.text}'", b.text.contains("%") || b.text.contains("{"))
                b.detail?.let { assertFalse("$lang: unfilled placeholder in '$it'", it.contains("%") || it.contains("{")) }
            }
        }
    }

    @Test
    fun theIconFollowsTheTurnNotTheLanguage() {
        for (lang in languages) {
            val c = context(lang)
            fun icon(t: Turn) = NavGuidance.of(c, state(NavPhase.ON_ROUTE, maneuver(t)), 0f).icon
            assertEquals(GuideIcon.LEFT, icon(Turn.LEFT))
            assertEquals(GuideIcon.LEFT, icon(Turn.SHARP_LEFT))
            assertEquals(GuideIcon.RIGHT, icon(Turn.SLIGHT_RIGHT))
            assertEquals(GuideIcon.U_TURN, icon(Turn.U_TURN))
        }
        assertEquals(GuideIcon.OFF_ROUTE, NavGuidance.of(context("en"), state(NavPhase.OFF_ROUTE), 0f).icon)
        assertEquals(GuideIcon.ARRIVED, NavGuidance.of(context("en"), state(NavPhase.ARRIVED), 0f).icon)
    }

    @Test
    fun everySpokenCueIsCompleteInEveryLanguage() {
        for (lang in languages) {
            val c = context(lang)
            val cues = listOf(
                Cue.Joined, Cue.OffRoute, Cue.BackOnRoute, Cue.Arrived,
                Cue.Ahead(maneuver(Turn.LEFT), 150), Cue.Ahead(maneuver(Turn.SHARP_RIGHT), 40),
                Cue.TurnNow(maneuver(Turn.U_TURN))
            )
            for (cue in cues) {
                val said = NavGuidance.speech(c, cue)
                assertTrue("$lang: $cue", said.isNotBlank() && !said.contains("%") && !said.contains("{"))
            }
        }
    }

    @Test
    fun directionTowardsTheRouteFollowsTheWayThePersonFaces() {
        val c = context("en")
        // Facing north (0): a target to the east (90) is to the right, west (270) to the left, south behind.
        assertEquals(c.getString(R.string.nav_turn_right), NavGuidance.directionTo(c, 0f, 90.0))
        assertEquals(c.getString(R.string.nav_turn_left), NavGuidance.directionTo(c, 0f, 270.0))
        assertEquals(c.getString(R.string.nav_turn_around), NavGuidance.directionTo(c, 0f, 180.0))
        assertEquals(c.getString(R.string.nav_continue_straight), NavGuidance.directionTo(c, 0f, 10.0))
        assertEquals(c.getString(R.string.nav_bear_right), NavGuidance.directionTo(c, 0f, 45.0))
        // Facing south (180): the same target to the east is now to the left.
        assertEquals(c.getString(R.string.nav_turn_left), NavGuidance.directionTo(c, 180f, 90.0))
        assertNull("no compass, no direction", NavGuidance.directionTo(c, null, 90.0))
        assertNull(NavGuidance.directionTo(c, 0f, null))
        assertNotNull(NavGuidance.directionTo(c, 359f, 1.0))
        assertNotEquals(c.getString(R.string.nav_turn_left), c.getString(R.string.nav_turn_right))
    }
}
