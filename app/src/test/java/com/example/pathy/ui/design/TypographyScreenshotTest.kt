package com.example.pathy.ui.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the type scale under each app language. The device locale decides the script tuning in
 * `pathyTypography`, so these tests set the locale through the Robolectric qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TypographyScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [34], qualifiers = "en-w412dp-h960dp-xxhdpi")
    fun english() = composeRule.captureTo("type_en", dark = false) {
        Specimen(
            head = "Discover hiking trails",
            body = "Record your trip, share your live location with friends and save maps for offline use.",
            label = "Wadi Rum Ridge · 8.4 km · Loop",
            mixed = "Trail: Wadi Rum  مسار  מסלול · 8.4 km"
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "iw-w412dp-h960dp-xxhdpi")
    fun hebrew() = composeRule.captureTo("type_he", dark = false) {
        Specimen(
            head = "גלו מסלולי הליכה",
            body = "הקליטו את הטיול, שתפו מיקום חי עם חברים ושמרו מפות לשימוש ללא חיבור לאינטרנט.",
            label = "מסלול ואדי רם · 8.4 ק״מ · מעגלי",
            mixed = "מסלול: Wadi Rum · 8.4 km · مسار"
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "ar-w412dp-h960dp-xxhdpi")
    fun arabic() = composeRule.captureTo("type_ar", dark = false) {
        Specimen(
            head = "اكتشف مسارات المشي",
            body = "سجّل رحلتك، وشارك أصدقاءك موقعك الحي، واحفظ الخرائط لاستخدامها دون اتصال بالإنترنت.",
            label = "مسار وادي رم · ٨٫٤ كم · دائري",
            mixed = "المسار: Wadi Rum · 8.4 km · מסלול"
        )
    }
}

@Composable
private fun Specimen(head: String, body: String, label: String, mixed: String) {
    val t = MaterialTheme.typography
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        val ink = MaterialTheme.colorScheme.onBackground
        val soft = MaterialTheme.colorScheme.onSurfaceVariant
        Text(head, style = t.displaySmall, color = ink)
        Text(head, style = t.headlineMedium, color = ink)
        Text(head, style = t.titleLarge, color = ink)
        Text(head, style = t.titleMedium, color = ink)
        Text(body, style = t.bodyLarge, color = soft)
        Text(body, style = t.bodyMedium, color = soft)
        Text(body, style = t.bodySmall, color = soft)
        Text(label, style = t.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = t.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(mixed, style = t.bodyLarge, color = ink)
    }
}
