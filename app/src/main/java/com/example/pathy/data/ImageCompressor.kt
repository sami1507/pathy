package com.example.pathy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Shrinks a photo before it is uploaded. A phone camera picture is 4-12 MB; a post needs about 300 KB.
 * Hikers often upload over a weak mobile connection, so this is the difference between a post that
 * publishes and one that waits for minutes.
 */
object ImageCompressor {
    const val MAX_SIDE_PX = 1600
    const val JPEG_QUALITY = 82

    /** The power-of-two shrink that BitmapFactory can apply while decoding, so a huge picture never fills memory. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxSide) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** The size to scale to so the longer side is at most [maxSide], keeping the shape. */
    fun fitWithin(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toFloat() / longest
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** JPEG bytes of the picture at [uri], upright and at most [MAX_SIDE_PX] on its longer side; null if it cannot be read. */
    fun compress(context: Context, uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

            val rotation = resolver.openInputStream(uri)?.use { rotationDegrees(ExifInterface(it)) } ?: 0
            val (w, h) = fitWithin(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (rotation != 0) postRotate(rotation.toFloat())
                if (w != decoded.width) postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val out = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (upright !== decoded) decoded.recycle()
            upright.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    internal fun rotationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
