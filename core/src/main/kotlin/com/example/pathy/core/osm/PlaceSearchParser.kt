package com.example.pathy.core.osm

import com.example.pathy.core.geo.LatLon
import com.google.gson.JsonParser

/** A place a person can search for and explore: a city, a park, a mountain, a village. */
data class Place(
    /** The short name in the app's language, for example "Haifa". */
    val name: String,
    /** The longer line that tells places of the same name apart, for example "Haifa, Haifa District, Israel". */
    val description: String,
    val position: LatLon,
    /** What it is: city, park, peak... as OpenStreetMap calls it. */
    val type: String
)

/** Reads the answer of the OpenStreetMap place-search service (Nominatim, `format=jsonv2`). */
object PlaceSearchParser {

    fun parse(json: String): List<Place> {
        val array = try {
            JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val places = ArrayList<Place>()
        for (element in array) {
            val o = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val lat = o.get("lat")?.asString?.toDoubleOrNull() ?: continue
            val lon = o.get("lon")?.asString?.toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val display = o.get("display_name")?.asString.orEmpty()
            val name = o.get("name")?.asString?.takeIf { it.isNotBlank() } ?: display.substringBefore(',').trim()
            if (name.isBlank()) continue
            places += Place(
                name = name,
                description = display,
                position = LatLon(lat, lon),
                type = o.get("type")?.asString.orEmpty()
            )
        }
        // The same place can come back twice (a city and its boundary): keep the first of each name+area.
        return places.distinctBy { it.name to (it.position.lat * 10).toInt() to (it.position.lon * 10).toInt() }
    }
}
