package com.example.pathy.offline

import android.content.Context
import com.example.pathy.data.TrailData
import com.google.gson.Gson
import java.io.File

/**
 * The trails kept on the phone. One folder per trail under `files/trails`, holding the trail itself
 * (`trail.json`: its line, numbers and conditions). The map around it is stored separately by Mapbox
 * (see [OfflineRegions]). Everything here is plain files, so the list opens with no connection at all.
 */
object OfflineTrails {

    /** A saved trail with the facts a list needs. */
    data class Saved(val trail: TrailData, val savedAtMs: Long, val sizeBytes: Long)

    fun root(context: Context): File = File(context.filesDir, "trails")

    /** Folder names stay safe whatever the trail id looks like. */
    internal fun folderName(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80).ifEmpty { "trail" }

    fun dirOf(root: File, id: String): File = File(root, folderName(id))

    fun fileOf(root: File, id: String): File = File(dirOf(root, id), "trail.json")

    /** Writes the trail so it can be opened later without a connection. The old file stays until the new one is complete. */
    fun save(root: File, trail: TrailData) {
        val dir = dirOf(root, trail.id)
        dir.mkdirs()
        val target = File(dir, "trail.json")
        val temp = File(dir, "trail.json.tmp")
        temp.writeText(Gson().toJson(trail))
        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
    }

    /** The saved trail, or null. The id inside the file must match, so two ids that map to one folder name never get mixed up. */
    fun load(root: File, id: String): TrailData? = read(fileOf(root, id))?.takeIf { it.id == id }

    fun isSaved(root: File, id: String): Boolean = load(root, id) != null

    /** Ids of every saved trail, for badges on lists. Cheap: it only looks at folder names. */
    fun ids(root: File): Set<String> =
        root.listFiles { f -> f.isDirectory }.orEmpty().filter { File(it, "trail.json").isFile }.mapNotNull { read(File(it, "trail.json"))?.id }.toSet()

    /** Newest first. A damaged file is skipped, not allowed to hide the rest. */
    fun list(root: File): List<Saved> =
        root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            val file = File(dir, "trail.json")
            val trail = read(file) ?: return@mapNotNull null
            Saved(trail, file.lastModified(), dir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
        }.sortedByDescending { it.savedAtMs }

    fun delete(root: File, id: String) {
        dirOf(root, id).deleteRecursively()
    }

    private fun read(file: File): TrailData? {
        if (!file.isFile) return null
        return runCatching { Gson().fromJson(file.readText(), TrailData::class.java) }
            .getOrNull()
            ?.takeIf { it.id.isNotBlank() && it.geoPoints.size >= 2 }
    }
}
