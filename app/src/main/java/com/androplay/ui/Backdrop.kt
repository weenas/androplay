package com.androplay.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * The music screen's backdrop: the album cover shrunk, blurred and later stretched over the
 * screen, as Apple Music and CarPlay do. Android's own blur (RenderEffect) needs Android 12;
 * blurring a tiny copy works everywhere and costs next to nothing.
 */
object Backdrop {
    /** Size of the blurred copy; stretched to the screen with smooth filtering. */
    private const val SIZE = 48
    private const val RADIUS = 4
    private const val PASSES = 3

    /** A blurred [SIZE]x[SIZE] version of the encoded [cover], or null if it can't be decoded. */
    fun fromCover(cover: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(cover, 0, cover.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        // Decode at a fraction of the size first: covers can be 1000+ px.
        options.inJustDecodeBounds = false
        options.inSampleSize = (minOf(options.outWidth, options.outHeight) / (SIZE * 2)).coerceAtLeast(1)
        val decoded = BitmapFactory.decodeByteArray(cover, 0, cover.size, options) ?: return null
        val small = Bitmap.createScaledBitmap(decoded, SIZE, SIZE, true)
        if (small !== decoded) decoded.recycle()
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        val blurred = blur(pixels, SIZE, SIZE, RADIUS, PASSES)
        return Bitmap.createBitmap(blurred, SIZE, SIZE, Bitmap.Config.ARGB_8888).also {
            if (small !== it) small.recycle()
        }
    }

    /**
     * A box blur of ARGB [pixels] ([width] x [height]), [passes] times, which approximates a
     * Gaussian. Edges repeat their outermost pixels. Opaque output.
     */
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int): IntArray {
        var current = pixels
        repeat(passes) {
            current = boxPass(current, width, height, radius, horizontal = true)
            current = boxPass(current, width, height, radius, horizontal = false)
        }
        return current
    }

    private fun boxPass(src: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val clamped = i.coerceIn(0, length - 1)
                return if (horizontal) src[line * width + clamped] else src[clamped * width + line]
            }
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = at(i)
                r += c shr 16 and 0xFF
                g += c shr 8 and 0xFF
                b += c and 0xFF
            }
            for (i in 0 until length) {
                val index = if (horizontal) line * width + i else i * width + line
                out[index] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val leaving = at(i - radius)
                val entering = at(i + radius + 1)
                r += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                g += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
        return out
    }
}
