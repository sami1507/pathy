package com.example.pathy.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.pathy.R
import com.example.pathy.core.gpx.Gpx
import com.example.pathy.core.track.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a route as a GPX file and hands it to the Android share sheet. */
object GpxShare {

    /** Creates the file in the app's cache (shared through a FileProvider) and returns the share intent. */
    suspend fun intentFor(context: Context, title: String, points: List<TrackPoint>): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Old exports are not needed once shared; keep the folder from growing.
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val file = File(dir, safeFileName(title) + ".gpx")
        file.writeText(Gpx.write(title, points), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, context.getString(R.string.activity_share_title))
    }

    /** A file name that is valid on every phone and computer, from a title in any language. */
    internal fun safeFileName(title: String): String {
        val cleaned = title.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").trim('_').take(60)
        return cleaned.ifEmpty { "pathy-route" }
    }
}
