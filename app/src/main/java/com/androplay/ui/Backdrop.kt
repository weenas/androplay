package com.androplay.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * The music screen's backdrop, in the style of Apple Music and CarPlay: the middle of the album
 * cover, heavily blurred, with its colours boosted (blurring averages them towards grey) and
 * only a light veil. Android's own blur (RenderEffect) needs Android 12; blurring a tiny copy
 * works everywhere, costs next to nothing, and the screen stretches it smoothly.
 */
object Backdrop {
    /** The blurred picture, and how dark a veil keeps white text readable over it. */
    class Result(val bitmap: Bitmap, val veil: Float)

    private const val SIZE = 64
    // Three box passes of radius 9 are close to a Gaussian of sigma ~9.5 px at this size.
    private const val RADIUS = 9
    private const val PASSES = 3
    /** Only the middle of the cover: its edges are often borders or text. */
    private const val CROP = 0.7f
    private const val SATURATION = 1.5f
    /** The veil over an average cover, and the most it gets over a bright one. */
    const val MIN_VEIL = 0.35f
    const val MAX_VEIL = 0.65f

    /** The backdrop for the encoded [cover], or null if it can't be decoded. */
    fun fromCover(cover: ByteArray): Result? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(cover, 0, cover.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        // Decode at a fraction of the size first: covers can be 1000+ px.
        options.inJustDecodeBounds = false
        options.inSampleSize = (minOf(options.outWidth, options.outHeight) / (SIZE * 2)).coerceAtLeast(1)
        val decoded = BitmapFactory.decodeByteArray(cover, 0, cover.size, options) ?: return null
        val cropWidth = (decoded.width * CROP).toInt().coerceAtLeast(1)
        val cropHeight = (decoded.height * CROP).toInt().coerceAtLeast(1)
        val middle = Bitmap.createBitmap(decoded, (decoded.width - cropWidth) / 2, (decoded.height - cropHeight) / 2, cropWidth, cropHeight)
        val small = Bitmap.createScaledBitmap(middle, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        listOf(decoded, middle, small).distinct().forEach { it.recycle() }
        val blurred = saturate(blur(pixels, SIZE, SIZE, RADIUS, PASSES), SATURATION)
        return Result(
            Bitmap.createBitmap(blurred, SIZE, SIZE, Bitmap.Config.ARGB_8888),
            veilFor(averageLuminance(blurred))
        )
    }

    /** Scales each pixel's distance from its own grey by [factor] (1 = unchanged). */
    fun saturate(pixels: IntArray, factor: Float): IntArray = IntArray(pixels.size) { i ->
        val c = pixels[i]
        val r = c shr 16 and 0xFF
        val g = c shr 8 and 0xFF
        val b = c and 0xFF
        val grey = luminance(r, g, b)
        fun boost(channel: Int) = (grey + (channel - grey) * factor).toInt().coerceIn(0, 255)
        (c and 0xFF000000.toInt()) or (boost(r) shl 16) or (boost(g) shl 8) or boost(b)
    }

    /** Mean luminance, 0 (black) to 1 (white). */
    fun averageLuminance(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 0f
        val total = pixels.sumOf { c -> luminance(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF).toDouble() }
        return (total / pixels.size / 255.0).toFloat()
    }

    /** A light veil for most covers, darker as they get brighter (white text sits on top). */
    fun veilFor(luminance: Float): Float =
        (MIN_VEIL + (luminance - 0.45f).coerceAtLeast(0f) * 0.6f).coerceAtMost(MAX_VEIL)

    /** Random grey noise, tiled faintly over the backdrop to hide banding. */
    fun grain(size: Int = 128, seed: Long = 7): Bitmap {
        val random = java.util.Random(seed)
        val pixels = IntArray(size * size) {
            val v = random.nextInt(256)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun luminance(r: Int, g: Int, b: Int) = 0.299f * r + 0.587f * g + 0.114f * b

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
