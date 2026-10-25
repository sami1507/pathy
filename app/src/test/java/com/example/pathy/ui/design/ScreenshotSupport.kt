package com.example.pathy.ui.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.example.pathy.ui.theme.TEMPATHYTheme
import java.io.File
import java.io.FileOutputStream

/**
 * Renders [content] inside the PATHY theme and writes `<name>.png` to the folder given by the
 * `pathy.shots.dir` system property (default: the system temp folder, in `pathy-design-shots`).
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.captureTo(
    name: String,
    dark: Boolean,
    content: @Composable () -> Unit
) {
    setContent {
        TEMPATHYTheme(darkTheme = dark) {
            content()
        }
    }
    waitForIdle()
    // Measure and draw the window view tree directly. Window-based capture waits for a
    // frame that Robolectric does not produce, so it times out.
    val metrics = activity.resources.displayMetrics
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val decor = activity.window.decorView
    decor.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    )
    decor.layout(0, 0, width, height)
    waitForIdle()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    decor.draw(Canvas(bitmap))
    val dir = File(System.getProperty("pathy.shots.dir") ?: System.getProperty("java.io.tmpdir"), "pathy-design-shots")
    dir.mkdirs()
    FileOutputStream(File(dir, "$name.png")).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}
