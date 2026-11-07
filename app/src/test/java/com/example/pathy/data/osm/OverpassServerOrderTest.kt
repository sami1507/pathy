package com.example.pathy.data.osm

import org.junit.Assert.assertEquals
import org.junit.Test

class OverpassServerOrderTest {

    private val all = listOf("fr", "de", "kumi", "coffee")
    private val now = 1_000_000L

    @Test fun withNoHistoryTheListedOrderIsKept() {
        assertEquals(all, OverpassClient.order(all, emptyMap(), null, now))
    }

    @Test fun theServerThatAnsweredLastTimeGoesFirst() {
        assertEquals(listOf("kumi", "fr", "de", "coffee"), OverpassClient.order(all, emptyMap(), "kumi", now))
    }

    @Test fun aServerThatJustFailedIsTriedLast() {
        val order = OverpassClient.order(all, mapOf("fr" to now - 10_000L), null, now)
        assertEquals(listOf("de", "kumi", "coffee", "fr"), order)
    }

    @Test fun aServerIsTriedAgainOnceTheCoolDownIsOver() {
        val order = OverpassClient.order(all, mapOf("fr" to now - 10 * 60_000L), null, now, coolDownMs = 5 * 60_000L)
        assertEquals(all, order)
    }

    @Test fun aFailedFavouriteDoesNotStayFirst() {
        val order = OverpassClient.order(all, mapOf("kumi" to now - 1_000L), lastGood = "kumi", now = now)
        assertEquals("kumi", order.last())
    }

    @Test fun everyServerIsStillTriedWhenAllHaveFailed() {
        val failed = all.associateWith { now - 1_000L }
        assertEquals(all.toSet(), OverpassClient.order(all, failed, null, now).toSet())
        assertEquals(4, OverpassClient.order(all, failed, null, now).size)
    }
}
