package com.example.pathy.offline

import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailInfo
import com.example.pathy.data.TrailTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineTrailsTest {

    private lateinit var root: File

    @Before fun setUp() {
        root = Files.createTempDirectory("pathy-trails").toFile()
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    private fun trail(id: String, name: String = "Wadi Qelt") = TrailData(
        id = id,
        name = name,
        geoPoints = listOf(LatLngDTO(31.8, 35.4), LatLngDTO(31.81, 35.42), LatLngDTO(31.82, 35.44)),
        midpoint = LatLngDTO(31.81, 35.42),
        isHikingTrail = true,
        distanceInMeters = 4200.0,
        durationInMillis = 90 * 60_000L,
        difficulty = DifficultyLevel.MEDIUM,
        locationName = "Judean Desert",
        tags = TrailTags(elevationGain = 310, maxElevation = 420),
        info = TrailInfo(waymark = "red", warnings = listOf("SEASONAL"), quality = 7)
    )

    @Test fun aSavedTrailComesBackExactlyAsItWasSaved() {
        val original = trail("relation_1")
        OfflineTrails.save(root, original)
        assertEquals(original, OfflineTrails.load(root, "relation_1"))
    }

    @Test fun elevationAndConditionsSurviveSoTheyShowWithNoConnection() {
        OfflineTrails.save(root, trail("relation_1"))
        val back = OfflineTrails.load(root, "relation_1")!!
        assertEquals(310, back.tags.elevationGain)
        assertEquals("red", back.info?.waymark)
        assertEquals(listOf("SEASONAL"), back.info?.warnings)
    }

    @Test fun savingAgainReplacesTheOldCopy() {
        OfflineTrails.save(root, trail("relation_1", "Old"))
        OfflineTrails.save(root, trail("relation_1", "New"))
        assertEquals("New", OfflineTrails.load(root, "relation_1")?.name)
        assertEquals(1, OfflineTrails.list(root).size)
        assertFalse(File(OfflineTrails.dirOf(root, "relation_1"), "trail.json.tmp").exists())
    }

    @Test fun idsAndIsSavedAnswerFromTheFilesAlone() {
        OfflineTrails.save(root, trail("a"))
        OfflineTrails.save(root, trail("b"))
        assertEquals(setOf("a", "b"), OfflineTrails.ids(root))
        assertTrue(OfflineTrails.isSaved(root, "a"))
        assertFalse(OfflineTrails.isSaved(root, "c"))
    }

    @Test fun deletingOneLeavesTheOthers() {
        OfflineTrails.save(root, trail("a"))
        OfflineTrails.save(root, trail("b"))
        OfflineTrails.delete(root, "a")
        assertNull(OfflineTrails.load(root, "a"))
        assertNotNull(OfflineTrails.load(root, "b"))
    }

    @Test fun theNewestTrailIsListedFirst() {
        OfflineTrails.save(root, trail("old"))
        OfflineTrails.save(root, trail("new"))
        File(OfflineTrails.dirOf(root, "old"), "trail.json").setLastModified(1_000L)
        assertEquals(listOf("new", "old"), OfflineTrails.list(root).map { it.trail.id })
    }

    @Test fun aDamagedFileIsSkippedAndDoesNotHideTheRest() {
        OfflineTrails.save(root, trail("good"))
        File(OfflineTrails.dirOf(root, "bad"), "trail.json").apply { parentFile!!.mkdirs(); writeText("{not json") }
        assertEquals(listOf("good"), OfflineTrails.list(root).map { it.trail.id })
        assertEquals(setOf("good"), OfflineTrails.ids(root))
    }

    @Test fun aTrailWithoutALineIsNotKept() {
        File(OfflineTrails.dirOf(root, "empty"), "trail.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"id":"empty","name":"x","geoPoints":[],"isHikingTrail":true,"locationName":""}""")
        }
        assertTrue(OfflineTrails.list(root).isEmpty())
    }

    @Test fun unsafeIdsStillMakeAUsableFolder() {
        val id = "osm/relation:42?x=../../etc"
        OfflineTrails.save(root, trail(id))
        assertEquals(id, OfflineTrails.load(root, id)?.id)
        // nothing was written outside the trails folder
        assertEquals(1, root.listFiles()!!.size)
    }

    @Test fun sizeCountsEveryFileOfTheTrail() {
        OfflineTrails.save(root, trail("a"))
        File(OfflineTrails.dirOf(root, "a"), "extra.bin").writeBytes(ByteArray(1000))
        val saved = OfflineTrails.list(root).single()
        assertTrue("size was ${saved.sizeBytes}", saved.sizeBytes >= 1000)
    }

    @Test fun anEmptyOrMissingFolderIsAnEmptyList() {
        assertTrue(OfflineTrails.list(File(root, "nothing")).isEmpty())
        assertTrue(OfflineTrails.ids(File(root, "nothing")).isEmpty())
    }
}
