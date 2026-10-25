package com.example.pathy.core.gpx

import com.example.pathy.core.track.TrackPoint
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/** A track read from a GPX file. */
data class GpxTrack(val name: String, val points: List<TrackPoint>)

/**
 * GPX 1.1 reading and writing. GPX is the format every hiking app, GPS watch and website exchanges,
 * so being able to export a recording and import a route is a basic expectation.
 */
object Gpx {

    fun write(name: String, points: List<TrackPoint>, creator: String = "PATHY"): String {
        val out = StringBuilder(64 + points.size * 90)
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<gpx version=\"1.1\" creator=\"").append(escape(creator)).append("\" ")
        out.append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        out.append("  <metadata><name>").append(escape(name)).append("</name></metadata>\n")
        out.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (p in points) {
            out.append("      <trkpt lat=\"").append(String.format(Locale.US, "%.6f", p.lat))
            out.append("\" lon=\"").append(String.format(Locale.US, "%.6f", p.lon)).append("\">")
            p.altitudeM?.let { out.append("<ele>").append(String.format(Locale.US, "%.1f", it)).append("</ele>") }
            if (p.timeMs > 0) {
                out.append("<time>")
                    .append(Instant.ofEpochMilli(p.timeMs).truncatedTo(ChronoUnit.SECONDS).toString())
                    .append("</time>")
            }
            out.append("</trkpt>\n")
        }
        out.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return out.toString()
    }

    /**
     * Reads the first track of a GPX file; a file that only has a route (rte) or loose waypoints
     * falls back to those. Throws [IllegalArgumentException] when no coordinates are found.
     */
    fun parse(input: InputStream): GpxTrack {
        val handler = GpxHandler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // GPX never needs external entities; refusing them blocks XXE.
            for (feature in listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd"
            )) {
                try { setFeature(feature, false) } catch (_: Exception) { /* parser does not know it */ }
            }
        }
        factory.newSAXParser().parse(input, handler)
        val points = handler.trackPoints.ifEmpty { handler.routePoints }.ifEmpty { handler.wayPoints }
        require(points.isNotEmpty()) { "The GPX file has no coordinates" }
        return GpxTrack(handler.bestName().ifBlank { "Imported track" }, points)
    }

    internal fun parseTime(value: String): Long {
        if (value.isEmpty()) return 0L
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try { OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: Exception) { 0L }
        }
    }

    private class GpxHandler : DefaultHandler() {
        val trackPoints = ArrayList<TrackPoint>()
        val routePoints = ArrayList<TrackPoint>()
        val wayPoints = ArrayList<TrackPoint>()

        private var trackName = ""
        private var routeName = ""
        private var metaName = ""

        private val text = StringBuilder()
        private val path = ArrayList<String>()
        private var lat = Double.NaN
        private var lon = Double.NaN
        private var ele: Double? = null
        private var time = 0L

        fun bestName() = trackName.ifBlank { routeName }.ifBlank { metaName }

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            path += qName
            text.setLength(0)
            if (qName == "trkpt" || qName == "rtept" || qName == "wpt") {
                lat = attrs.getValue("lat")?.toDoubleOrNull() ?: Double.NaN
                lon = attrs.getValue("lon")?.toDoubleOrNull() ?: Double.NaN
                ele = null
                time = 0L
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            val value = text.toString().trim()
            val parent = path.getOrNull(path.size - 2)
            when (qName) {
                "ele" -> ele = value.toDoubleOrNull()
                "time" -> if (parent == "trkpt" || parent == "rtept" || parent == "wpt") time = parseTime(value)
                "name" -> when (parent) {
                    "trk" -> if (trackName.isEmpty()) trackName = value
                    "rte" -> if (routeName.isEmpty()) routeName = value
                    "metadata" -> if (metaName.isEmpty()) metaName = value
                }
                "trkpt" -> addPoint(trackPoints)
                "rtept" -> addPoint(routePoints)
                "wpt" -> addPoint(wayPoints)
            }
            text.setLength(0)
            if (path.isNotEmpty()) path.removeAt(path.size - 1)
        }

        private fun addPoint(into: MutableList<TrackPoint>) {
            if (!lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                into += TrackPoint(lat, lon, ele, time, null)
            }
            lat = Double.NaN
            lon = Double.NaN
        }
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
