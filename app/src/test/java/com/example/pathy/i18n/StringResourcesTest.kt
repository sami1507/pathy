package com.example.pathy.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Guards the three translations (English, Arabic, Hebrew) against drifting apart. It reads the
 * strings.xml files directly, so it needs no Android runtime.
 */
class StringResourcesTest {

    private class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>
    )

    private val resDir: File = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private fun load(folder: String): Resources {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "$folder/strings.xml"))
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            when (node.tagName) {
                "string" -> strings[node.getAttribute("name")] = node.textContent
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val children = node.getElementsByTagName("item")
                    for (k in 0 until children.length) {
                        val item = children.item(k) as Element
                        items[item.getAttribute("quantity")] = item.textContent
                    }
                    plurals[node.getAttribute("name")] = items
                }
            }
        }
        return Resources(strings, plurals)
    }

    private val en = load("values")
    private val ar = load("values-ar")
    private val he = load("values-iw")

    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdf]").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyStringExistsInAllThreeLanguages() {
        val missing = mutableListOf<String>()
        for ((name, res) in listOf("English" to en, "Arabic" to ar, "Hebrew" to he)) {
            (en.strings.keys + ar.strings.keys + he.strings.keys).filter { it !in res.strings }
                .forEach { missing += "$name is missing string '$it'" }
            (en.plurals.keys + ar.plurals.keys + he.plurals.keys).filter { it !in res.plurals }
                .forEach { missing += "$name is missing plurals '$it'" }
        }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun placeholdersMatchAcrossLanguages() {
        val bad = mutableListOf<String>()
        for (key in en.strings.keys) {
            val reference = placeholders(en.strings.getValue(key))
            for ((name, res) in listOf("Arabic" to ar, "Hebrew" to he)) {
                val other = res.strings[key] ?: continue
                if (placeholders(other) != reference) {
                    bad += "'$key' $name uses ${placeholders(other)} but English uses $reference"
                }
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun pluralsHaveTheFormsEachLanguageNeeds() {
        val problems = mutableListOf<String>()
        val required = mapOf(
            "English" to (en to setOf("one", "other")),
            "Arabic" to (ar to setOf("zero", "one", "two", "few", "many", "other")),
            "Hebrew" to (he to setOf("one", "two", "other"))
        )
        for ((name, pair) in required) {
            val (res, quantities) = pair
            for ((key, items) in res.plurals) {
                val missing = quantities - items.keys
                if (missing.isNotEmpty()) problems += "$name plurals '$key' lacks $missing"
            }
        }
        // Each form of a plural must use the same number placeholder style as the English "other".
        for (key in en.plurals.keys) {
            for ((name, res) in listOf("Arabic" to ar, "Hebrew" to he)) {
                res.plurals[key]?.forEach { (quantity, text) ->
                    if (text.contains("%") && !text.contains("%d")) problems += "$name plurals '$key/$quantity' has a non-%d placeholder"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Names that stay in Latin letters in every language. */
    private val brandNames = Regex("PATHY|Scout|Google|Maps|SOS|SMS|MTB|P2P|WiFi|Wi-Fi")

    @Test
    fun arabicAndHebrewAreActuallyTranslated() {
        val arabic = Regex("[؀-ۿ]")
        val hebrew = Regex("[֐-׿]")
        val untranslated = mutableListOf<String>()
        for ((key, text) in en.strings) {
            val withoutNames = text.replace(Regex("%\\d+\\$[sdf]"), "").replace(brandNames, "")
            val latinWords = Regex("[A-Za-z]{3,}").findAll(withoutNames).count()
            if (latinWords == 0) continue
            ar.strings[key]?.let { if (!arabic.containsMatchIn(it)) untranslated += "Arabic '$key' has no Arabic text: $it" }
            he.strings[key]?.let { if (!hebrew.containsMatchIn(it)) untranslated += "Hebrew '$key' has no Hebrew text: $it" }
        }
        assertTrue(untranslated.joinToString("\n"), untranslated.isEmpty())
    }

    @Test
    fun noStringIsEmptyOrHasUnescapedApostrophes() {
        val bad = mutableListOf<String>()
        for ((name, res) in listOf("English" to en, "Arabic" to ar, "Hebrew" to he)) {
            res.strings.forEach { (key, text) ->
                if (text.isBlank()) bad += "$name '$key' is empty"
                // A straight apostrophe in Hebrew should be a geresh (U+05F3) or gershayim (U+05F4).
                if (name == "Hebrew" && Regex("[א-ת]['\"]").containsMatchIn(text) && !text.contains("\\")) {
                    bad += "Hebrew '$key' uses a straight quote after a letter: $text"
                }
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
