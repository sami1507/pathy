package com.example.pathy.offline

import com.example.pathy.data.LatLngDTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRegionsTest {

    private val short = listOf(LatLngDTO(32.00, 35.00), LatLngDTO(32.02, 35.02))

    @Test
    fun theSavedAreaCoversTheWholeTrailWithAMargin() {
        val (area, spanKm) = OfflineRegions.areaAround(short)
        val ring = area.coordinates().single()
        assertEquals("a closed square", ring.first(), ring.last())
        assertTrue(ring.minOf { it.latitude() } < 32.00)
        assertTrue(ring.maxOf { it.latitude() } > 32.02)
        assertTrue(ring.minOf { it.longitude() } < 35.00)
        assertTrue(ring.maxOf { it.longitude() } > 35.02)
        assertTrue("a 2 km trail is a small download", spanKm in 2.0..4.0)
    }

    @Test
    fun longerTrailsGetLessDetailSoTheDownloadStaysSmall() {
        assertEquals(16.toByte(), OfflineRegions.maxZoomFor(5.0))
        assertEquals(15.toByte(), OfflineRegions.maxZoomFor(15.0))
        assertEquals(14.toByte(), OfflineRegions.maxZoomFor(40.0))
        assertEquals(13.toByte(), OfflineRegions.maxZoomFor(200.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aTrailWithoutALineCannotBeSaved() {
        OfflineRegions.areaAround(listOf(LatLngDTO(32.0, 35.0)))
    }

    @Test
    fun eachTrailHasItsOwnRegion() {
        assertEquals("trail-abc", OfflineRegions.regionId("abc"))
    }
}
