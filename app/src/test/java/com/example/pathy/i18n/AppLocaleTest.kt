package com.example.pathy.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLocaleTest {

    @Test
    fun deviceLanguageIsUsedWhenPathyHasIt() {
        assertEquals(AppLocale.ARABIC, AppLocale.resolve(AppLocale.SYSTEM, listOf(Locale("ar", "IL"))))
        assertEquals(AppLocale.HEBREW, AppLocale.resolve(AppLocale.SYSTEM, listOf(Locale("iw", "IL"))))
        assertEquals(AppLocale.HEBREW, AppLocale.resolve(AppLocale.SYSTEM, listOf(Locale.forLanguageTag("he-IL"))))
        assertEquals(AppLocale.ENGLISH, AppLocale.resolve(AppLocale.SYSTEM, listOf(Locale.US)))
    }

    @Test
    fun firstSupportedDeviceLanguageWins() {
        val device = listOf(Locale.FRENCH, Locale("ar"), Locale.US)
        assertEquals(AppLocale.ARABIC, AppLocale.resolve(AppLocale.SYSTEM, device))
    }

    @Test
    fun unsupportedDeviceLanguageFallsBackToEnglishNotHebrew() {
        assertEquals(AppLocale.ENGLISH, AppLocale.resolve(AppLocale.SYSTEM, listOf(Locale.FRENCH, Locale.GERMAN)))
        assertEquals(AppLocale.ENGLISH, AppLocale.resolve(AppLocale.SYSTEM, emptyList()))
    }

    @Test
    fun anExplicitChoiceBeatsTheDevice() {
        assertEquals(AppLocale.HEBREW, AppLocale.resolve(AppLocale.HEBREW, listOf(Locale.US)))
        assertEquals(AppLocale.ARABIC, AppLocale.resolve("ar", listOf(Locale("iw"))))
        // A legacy or region-tagged code is understood too.
        assertEquals(AppLocale.HEBREW, AppLocale.resolve("iw", listOf(Locale.US)))
        assertEquals(AppLocale.ARABIC, AppLocale.resolve("ar-EG", listOf(Locale.US)))
    }

    @Test
    fun arabicUsesWesternDigitsAndLatinNumbersLineUpWithUnits() {
        val ar = AppLocale.localeFor(AppLocale.ARABIC)
        assertEquals("1234", String.format(ar, "%d", 1234))
        assertEquals("12.5", String.format(ar, "%.1f", 12.5))
        assertEquals("ar", ar.language)
    }

    @Test
    fun hebrewAndEnglishFormatNumbersTheSame() {
        assertEquals("12.5", String.format(AppLocale.localeFor(AppLocale.HEBREW), "%.1f", 12.5))
        assertEquals("12.5", String.format(AppLocale.localeFor(AppLocale.ENGLISH), "%.1f", 12.5))
    }

    @Test
    fun rightToLeftLanguages() {
        assertTrue(AppLocale.isRtl(AppLocale.ARABIC))
        assertTrue(AppLocale.isRtl(AppLocale.HEBREW))
        assertFalse(AppLocale.isRtl(AppLocale.ENGLISH))
    }

    @Test
    fun hebrewLocaleIsTheLegacyCodeAndroidResourcesUse() {
        val code = AppLocale.localeFor(AppLocale.HEBREW).language
        assertTrue("iw or he expected but was $code", code == "iw" || code == "he")
    }
}
