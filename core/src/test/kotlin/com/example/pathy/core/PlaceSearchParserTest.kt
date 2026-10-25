package com.example.pathy.core

import com.example.pathy.core.osm.PlaceSearchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchParserTest {

    @Test
    fun aRealAnswerInArabicGivesPlacesWithPositions() {
        val json = javaClass.getResourceAsStream("/nominatim_chamonix_ar.json")!!.bufferedReader().readText()
        val places = PlaceSearchParser.parse(json)
        assertTrue("found places", places.isNotEmpty())
        val first = places.first()
        assertTrue("name is not empty", first.name.isNotBlank())
        assertTrue("it is in the Alps", first.position.lat in 45.0..46.5 && first.position.lon in 6.0..7.5)
        assertTrue("description tells it apart from other places", first.description.length > first.name.length)
    }

    @Test
    fun badOrEmptyAnswersGiveNoPlacesAndNoCrash() {
        assertTrue(PlaceSearchParser.parse("").isEmpty())
        assertTrue(PlaceSearchParser.parse("<html>busy</html>").isEmpty())
        assertTrue(PlaceSearchParser.parse("{}").isEmpty())
        assertTrue(PlaceSearchParser.parse("[]").isEmpty())
    }

    @Test
    fun entriesWithoutValidCoordinatesAreSkipped() {
        val json = """[
          {"lat":"abc","lon":"1","display_name":"Broken, X"},
          {"lat":"95","lon":"1","display_name":"Off the map, X"},
          {"lat":"32.79","lon":"34.99","name":"Haifa","display_name":"Haifa, Israel","type":"city"}
        ]"""
        val places = PlaceSearchParser.parse(json)
        assertEquals(listOf("Haifa"), places.map { it.name })
    }

    @Test
    fun theSamePlaceListedTwiceIsKeptOnce() {
        val json = """[
          {"lat":"32.79","lon":"34.99","name":"Haifa","display_name":"Haifa, Israel","type":"city"},
          {"lat":"32.7901","lon":"34.9902","name":"Haifa","display_name":"Haifa, District","type":"administrative"}
        ]"""
        assertEquals(1, PlaceSearchParser.parse(json).size)
    }
}
