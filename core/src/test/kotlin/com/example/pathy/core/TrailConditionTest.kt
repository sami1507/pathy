package com.example.pathy.core

import com.example.pathy.core.trail.ConditionLevel
import com.example.pathy.core.trail.ConditionReport
import com.example.pathy.core.trail.ConditionStatus
import com.example.pathy.core.trail.ConditionStatus.BLOCKED
import com.example.pathy.core.trail.ConditionStatus.CLOSED
import com.example.pathy.core.trail.ConditionStatus.MUDDY
import com.example.pathy.core.trail.ConditionStatus.NOT_FOUND
import com.example.pathy.core.trail.ConditionStatus.OPEN
import com.example.pathy.core.trail.TrailCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrailConditionTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun r(status: ConditionStatus, daysAgo: Int, user: String) = ConditionReport(status, now - daysAgo * day, user)

    @Test
    fun noReportsGivesNoSummary() {
        assertNull(TrailCondition.summarize(emptyList(), now))
    }

    @Test
    fun reportsOlderThanSixWeeksAreIgnored() {
        assertNull(TrailCondition.summarize(listOf(r(BLOCKED, 60, "a")), now))
        assertNotNull(TrailCondition.summarize(listOf(r(BLOCKED, 10, "a")), now))
    }

    @Test
    fun theNewestReportDecidesTheColour() {
        val s = TrailCondition.summarize(listOf(r(BLOCKED, 9, "a"), r(OPEN, 1, "b")), now)!!
        assertEquals(OPEN, s.latest)
        assertEquals(ConditionLevel.GOOD, s.level)
        val worse = TrailCondition.summarize(listOf(r(OPEN, 9, "a"), r(CLOSED, 1, "b")), now)!!
        assertEquals(ConditionLevel.PROBLEM, worse.level)
    }

    @Test
    fun agreementIsCountedByColour() {
        val s = TrailCondition.summarize(
            listOf(r(BLOCKED, 1, "a"), r(CLOSED, 2, "b"), r(NOT_FOUND, 3, "c"), r(OPEN, 4, "d"), r(MUDDY, 5, "e")), now
        )!!
        assertEquals(ConditionLevel.PROBLEM, s.level)
        assertEquals(3, s.agreeing)
        assertEquals(5, s.total)
    }

    @Test
    fun oneAccountCountsOnceWithItsNewestReport() {
        val s = TrailCondition.summarize(listOf(r(BLOCKED, 5, "a"), r(BLOCKED, 4, "a"), r(OPEN, 1, "a")), now)!!
        assertEquals(1, s.total)
        assertEquals(OPEN, s.latest)
    }

    @Test
    fun aClockSetFarInTheFutureDoesNotWinForever() {
        assertNull(TrailCondition.summarize(listOf(ConditionReport(BLOCKED, now + 30 * day, "a")), now))
    }
}
