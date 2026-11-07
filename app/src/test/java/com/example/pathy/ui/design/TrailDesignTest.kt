package com.example.pathy.ui.design

import androidx.compose.ui.geometry.Offset
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.LatLngDTO
import com.example.pathy.offline.NetworkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrailDesignTest {

    private val diagonal = listOf(LatLngDTO(31.0, 35.0), LatLngDTO(31.1, 35.1), LatLngDTO(31.2, 35.2))

    @Test fun theRouteStaysInsideTheBoxWithItsPadding() {
        val points = projectRoute(diagonal, width = 200f, height = 100f, padding = 10f)
        assertEquals(3, points.size)
        points.forEach {
            assertTrue("x=${it.x}", it.x in 10f - 0.01f..190f + 0.01f)
            assertTrue("y=${it.y}", it.y in 10f - 0.01f..90f + 0.01f)
        }
    }

    @Test fun northIsUpAndEastIsRight() {
        val points = projectRoute(diagonal, 200f, 200f, 10f)
        assertTrue("the last point is further north, so higher on screen", points.last().y < points.first().y)
        assertTrue("the last point is further east, so further right", points.last().x > points.first().x)
    }

    @Test fun theShapeIsNotStretched() {
        // A square of ground: one degree of latitude is taller than one degree of longitude is wide, away from the equator.
        val square = listOf(LatLngDTO(60.0, 10.0), LatLngDTO(61.0, 10.0), LatLngDTO(61.0, 12.0), LatLngDTO(60.0, 12.0))
        val points = projectRoute(square, 300f, 300f, 0f)
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        // At 60.5 degrees north, two degrees of longitude are about as wide as one degree of latitude is tall.
        assertEquals(1.0, (width / height).toDouble(), 0.05)
    }

    @Test fun theRouteIsCentred() {
        val points = projectRoute(diagonal, 400f, 100f, 10f)
        val left = points.minOf { it.x }
        val right = 400f - points.maxOf { it.x }
        assertEquals(left, right, 0.5f)
    }

    @Test fun aStraightEastWestLineStillFitsAndIsCentred() {
        val line = listOf(LatLngDTO(31.0, 35.0), LatLngDTO(31.0, 35.2))
        val points = projectRoute(line, 200f, 100f, 10f)
        assertEquals(points[0].y, points[1].y, 0.001f)
        assertEquals(50f, points[0].y, 0.5f)
        assertEquals(10f, points.minOf { it.x }, 0.5f)
    }

    @Test fun aSinglePointIsDrawnInTheMiddleAndNothingCrashes() {
        val points = projectRoute(listOf(LatLngDTO(31.0, 35.0), LatLngDTO(31.0, 35.0)), 100f, 80f, 10f)
        points.forEach { assertEquals(Offset(50f, 40f), it) }
    }

    @Test fun nothingToDrawGivesNothing() {
        assertTrue(projectRoute(emptyList(), 100f, 100f, 5f).isEmpty())
        assertTrue(projectRoute(diagonal, 0f, 100f, 5f).isEmpty())
    }

    @Test fun downsamplingKeepsBothEndsAndTheRequestedSize() {
        val many = List(5000) { it }
        val few = downsample(many, 100)
        assertEquals(100, few.size)
        assertEquals(0, few.first())
        assertEquals(4999, few.last())
        assertEquals(few.sorted(), few)
    }

    @Test fun aShortTrailIsLeftAlone() {
        val short = listOf(1, 2, 3)
        assertEquals(short, downsample(short, 100))
    }

    @Test fun aTrailThatEndsWhereItStartedIsALoop() {
        val loop = listOf(Offset(10f, 10f), Offset(90f, 20f), Offset(50f, 80f), Offset(11f, 11f))
        assertTrue(isLoop(loop, tolerance = 4f))
        assertFalse(isLoop(listOf(Offset(0f, 0f), Offset(50f, 50f), Offset(100f, 100f)), tolerance = 4f))
        assertFalse("two points are a line, not a loop", isLoop(listOf(Offset(0f, 0f), Offset(1f, 1f)), tolerance = 4f))
    }

    @Test fun everyDifficultyHasItsOwnColour() {
        val colours = DifficultyLevel.values().map { difficultyColor(it) }
        assertEquals(colours.size, colours.toSet().size)
        assertNotEquals(difficultyColor(DifficultyLevel.EASY), difficultyColor(DifficultyLevel.HARD))
    }

    @Test fun aNetworkWithoutVerifiedInternetCountsAsOffline() {
        assertTrue(NetworkState.hasInternet(hasInternetCapability = true, validated = true))
        assertFalse("captive portal or dead hotspot", NetworkState.hasInternet(hasInternetCapability = true, validated = false))
        assertFalse(NetworkState.hasInternet(hasInternetCapability = false, validated = true))
    }

    @Test fun projectionIsDeterministic() {
        val a = projectRoute(diagonal, 120f, 120f, 8f)
        val b = projectRoute(diagonal, 120f, 120f, 8f)
        a.zip(b).forEach { (p, q) -> assertTrue(abs(p.x - q.x) < 1e-6 && abs(p.y - q.y) < 1e-6) }
    }
}
