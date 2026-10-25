package com.example.pathy.data.conditions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailConditionRepositoryTest {

    @Test
    fun onlyPlainTrailIdsAreUsedAsDocumentNames() {
        assertEquals("osm_r123", TrailConditionRepository.safeKey("osm_r123"))
        assertEquals("gpx_ab12cd", TrailConditionRepository.safeKey("gpx_ab12cd"))
        assertNull(TrailConditionRepository.safeKey("a/b"))
        assertNull(TrailConditionRepository.safeKey(""))
        assertNull(TrailConditionRepository.safeKey("x".repeat(101)))
}
}
