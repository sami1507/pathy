package com.example.pathy.ui.design

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the visual identity at the source level. A screen that calls the plain `setContent`, or wraps
 * itself in Material's default `MaterialTheme { }`, runs on the stock purple theme and system font. That
 * went unnoticed for thirteen screens because every screenshot test wraps its content in the app theme
 * by hand, so the only place it showed was a real phone.
 */
class ThemeUsageTest {

    private fun sources(): List<File> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
        assertTrue("could not find src/main/java from ${File(".").absolutePath}", root != null)
        return root!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun offenders(pattern: Regex, allowedFile: String): List<String> =
        sources().filter { it.name != allowedFile }
            .filter { pattern.containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()

    @Test fun everyScreenThatSetsContentAppliesTheAppTheme() {
        val plain = Regex("""(?<![A-Za-z])setContent\s*\{""")
        val found = sources()
            .filter { it.name != "Theme.kt" }
            .filter { file ->
                val text = file.readText()
                plain.containsMatchIn(text) && !text.contains("TEMPATHYTheme")
            }
            .map { it.name }
            .sorted()
        assertTrue("use setPathyContent { } or wrap the content in TEMPATHYTheme: $found", found.isEmpty())
    }

    @Test fun noScreenWrapsItselfInTheDefaultMaterialTheme() {
        val found = offenders(Regex("""(?<![A-Za-z.])MaterialTheme\s*[({]"""), allowedFile = "Theme.kt")
        assertTrue("use TEMPATHYTheme instead of MaterialTheme: $found", found.isEmpty())
    }
}
