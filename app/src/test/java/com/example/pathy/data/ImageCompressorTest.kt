package com.example.pathy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun smallPicturesAreLeftAlone() {
        assertEquals(1, ImageCompressor.sampleSize(1200, 800))
        assertEquals(1200 to 800, ImageCompressor.fitWithin(1200, 800))
    }

    @Test
    fun aTwelveMegapixelPhotoIsShrunkWhileDecoding() {
        // 4000 x 3000: decode at half size (2000 px), then scale the rest of the way down.
        assertEquals(2, ImageCompressor.sampleSize(4000, 3000))
        assertEquals(1600 to 1200, ImageCompressor.fitWithin(2000, 1500))
    }

    @Test
    fun aPanoramaIsFitByItsLongSide() {
        assertEquals(1600 to 400, ImageCompressor.fitWithin(8000, 2000))
        assertEquals(4, ImageCompressor.sampleSize(8000, 2000))
    }

    @Test
    fun portraitPicturesAreFitByTheirHeight() {
        assertEquals(1200 to 1600, ImageCompressor.fitWithin(3000, 4000))
    }

    @Test
    fun anAbsurdlyThinPictureNeverShrinksToNothing() {
        val (w, h) = ImageCompressor.fitWithin(100_000, 1)
        assertEquals(1600, w)
        assertEquals(1, h)
    }
}
