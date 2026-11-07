package com.example.pathy.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A saved trail downloads the map in one style only ([OfflineRegions.STYLE]). Any screen that opens a
 * saved trail and asks for another style gets a black rectangle with no signal: the guidance screen did
 * exactly that with Mapbox Standard, and it only showed on a phone in airplane mode.
 */
class OfflineMapStyleTest {

    private fun source(name: String): String {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
        assertTrue("could not find src/main/java from ${File(".").absolutePath}", root != null)
        val file = root!!.walkTopDown().first { it.isFile && it.name == name }
        return file.readText()
    }

    @Test fun theScreensThatFollowOrShowASavedTrailUseTheDownloadedStyle() {
        listOf("TrailNavigationScreen.kt", "TrailDetailsScreen.kt").forEach { name ->
            val text = source(name)
            assertTrue("$name must load OfflineRegions.STYLE", text.contains("loadStyleUri(OfflineRegions.STYLE)"))
            assertFalse("$name must not load another style", Regex("""loadStyleUri\(Style\.""").containsMatchIn(text))
        }
    }

    @Test fun theDownloadedStyleIsTheOutdoorsStyle() {
        assertTrue(OfflineRegions.STYLE.contains("outdoors"))
    }
}
